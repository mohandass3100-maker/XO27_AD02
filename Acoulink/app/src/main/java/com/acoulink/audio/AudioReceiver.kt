package com.acoulink.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.acoulink.protocol.Packet
import com.acoulink.protocol.PacketDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Communication states emitted during acoustic reception.
 */
enum class ReceiverState {
    IDLE,
    LISTENING,
    SIGNAL_DETECTED,
    SYNCHRONIZING,
    RECEIVING,
    STOPPED,
    ERROR
}

/**
 * Listener interface for acoustic reception events and decoded packet callbacks.
 */
interface ReceiverEventListener {
    fun onStateChanged(state: ReceiverState, signalQualityPercent: Int = 0)
    fun onPacketDecoded(packet: Packet)
    fun onError(message: String)
}

/**
 * Captures microphone audio using AudioRecord, detects preambles,
 * demodulates BFSK symbols using the Goertzel algorithm, and yields verified packets.
 */
class AudioReceiver(
    private val config: AudioConfig = AudioConfig()
) {
    private val signalDetector = SignalDetector(config)
    private val demodulator = FSKDemodulator(config)
    private val packetDecoder = PacketDecoder()

    private val isListening = AtomicBoolean(false)
    private var activeAudioRecord: AudioRecord? = null

    /**
     * Starts continuous acoustic recording and demodulation on the IO dispatcher.
     */
    @SuppressLint("MissingPermission")
    suspend fun startListening(listener: ReceiverEventListener) = withContext(Dispatchers.IO) {
        if (isListening.getAndSet(true)) {
            listener.onError("Receiver is already running")
            return@withContext
        }

        var audioRecord: AudioRecord? = null
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                config.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                listener.onError("Failed to query valid AudioRecord buffer configuration")
                return@withContext
            }

            val bufferSize = (minBufferSize * 2).coerceAtLeast(8192)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                listener.onError("AudioRecord initialization failed. Verify microphone permissions.")
                return@withContext
            }

            activeAudioRecord = audioRecord
            audioRecord.startRecording()

            listener.onStateChanged(ReceiverState.LISTENING)
            packetDecoder.reset()
            signalDetector.reset()

            val readBuffer = ShortArray(config.samplesPerSymbol)
            val bitStream = mutableListOf<Int>()
            var currentState = ReceiverState.LISTENING

            while (isListening.get()) {
                val samplesRead = audioRecord.read(readBuffer, 0, readBuffer.size)
                if (samplesRead <= 0) continue

                // 1. Analyze signal & pilot tone presence
                val detection = signalDetector.processChunk(readBuffer, 0, samplesRead)

                when (currentState) {
                    ReceiverState.LISTENING -> {
                        if (detection.isSignalDetected) {
                            currentState = ReceiverState.SIGNAL_DETECTED
                            listener.onStateChanged(currentState, detection.signalQualityPercent)
                        }
                    }

                    ReceiverState.SIGNAL_DETECTED -> {
                        if (detection.isSynchronized) {
                            currentState = ReceiverState.SYNCHRONIZING
                            listener.onStateChanged(currentState, detection.signalQualityPercent)
                        } else if (!detection.isSignalDetected) {
                            currentState = ReceiverState.LISTENING
                            listener.onStateChanged(currentState, detection.signalQualityPercent)
                        }
                    }

                    ReceiverState.SYNCHRONIZING -> {
                        // When pilot ends (transition from pilot tone to guard/data), transition to RECEIVING
                        if (!detection.isSignalDetected) {
                            currentState = ReceiverState.RECEIVING
                            listener.onStateChanged(currentState, detection.signalQualityPercent)
                            bitStream.clear()
                        }
                    }

                    ReceiverState.RECEIVING -> {
                        // Demodulate BFSK symbol window
                        val symbol = demodulator.demodulateSymbolWindow(readBuffer, 0, samplesRead)
                        bitStream.add(symbol.bit)

                        // When we have accumulated full bytes (multiples of 8 bits)
                        if (bitStream.size >= 8 && bitStream.size % 8 == 0) {
                            val chunkBytes = demodulator.bitsToBytes(bitStream)
                            val discoveredPackets = packetDecoder.feedBytes(chunkBytes)
                            for (packet in discoveredPackets) {
                                listener.onPacketDecoded(packet)
                            }
                            bitStream.clear()
                        }

                        // If signal dropped completely for extended period, revert to LISTENING
                        if (detection.snr < 1.1 && !symbol.isConfident && bitStream.size > 256) {
                            currentState = ReceiverState.LISTENING
                            listener.onStateChanged(currentState, 0)
                            bitStream.clear()
                        }
                    }

                    else -> Unit
                }
            }

            listener.onStateChanged(ReceiverState.STOPPED)
        } catch (e: CancellationException) {
            listener.onStateChanged(ReceiverState.STOPPED)
            throw e
        } catch (e: Exception) {
            listener.onError("Audio receiver error: ${e.message ?: "Unknown error"}")
        } finally {
            isListening.set(false)
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
            activeAudioRecord = null
        }
    }

    /**
     * Stops the microphone recording and demodulation engine.
     */
    fun stop() {
        isListening.set(false)
    }

    /**
     * Simulates receiving a decoded packet directly. Used for hackathon demo testing.
     */
    fun injectSimulatedPacket(packet: Packet, listener: ReceiverEventListener) {
        listener.onStateChanged(ReceiverState.RECEIVING, 95)
        listener.onPacketDecoded(packet)
    }

    val isActive: Boolean
        get() = isListening.get()
}
