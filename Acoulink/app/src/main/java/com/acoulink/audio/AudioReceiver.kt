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
import java.util.ArrayDeque
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

            val bufferSize = (minBufferSize * 4).coerceAtLeast(16384)
            val audioSources = intArrayOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.MIC
            )

            var createdRecord: AudioRecord? = null
            for (source in audioSources) {
                try {
                    val record = AudioRecord(
                        source,
                        config.sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        createdRecord = record
                        break
                    } else {
                        record.release()
                    }
                } catch (_: Exception) {}
            }

            if (createdRecord == null) {
                listener.onError("AudioRecord initialization failed. Verify microphone permissions.")
                return@withContext
            }

            audioRecord = createdRecord
            activeAudioRecord = audioRecord
            audioRecord.startRecording()

            listener.onStateChanged(ReceiverState.LISTENING)
            packetDecoder.reset()
            signalDetector.reset()

            val readChunkSize = 512
            val readBuffer = ShortArray(readChunkSize)
            val symbolAccumulator = ArrayDeque<Short>(config.samplesPerSymbol * 4)
            val bitStream = mutableListOf<Int>()
            val frameBits = mutableListOf<Int>()
            var isPreambleLocked = false
            var expectedTotalFrameBits = -1
            var currentState = ReceiverState.LISTENING
            var pilotChunksHeard = 0

            while (isListening.get()) {
                val samplesRead = audioRecord.read(readBuffer, 0, readBuffer.size)
                if (samplesRead <= 0) continue

                when (currentState) {
                    ReceiverState.LISTENING -> {
                        val detection = signalDetector.processChunk(readBuffer, 0, samplesRead)
                        if (detection.isSignalDetected) {
                            listener.onStateChanged(ReceiverState.SIGNAL_DETECTED, detection.signalQualityPercent)
                            if (detection.isSynchronized) {
                                currentState = ReceiverState.SYNCHRONIZING
                                pilotChunksHeard = 3
                                listener.onStateChanged(ReceiverState.SYNCHRONIZING, detection.signalQualityPercent)
                            }
                        }
                    }

                    ReceiverState.SYNCHRONIZING -> {
                        pilotChunksHeard++
                        val dataStarted = signalDetector.hasDataStarted(readBuffer, 0, samplesRead)
                        val detection = signalDetector.processChunk(readBuffer, 0, samplesRead)

                        if (dataStarted || (!detection.isSignalDetected && pilotChunksHeard >= 4)) {
                            // Transition from Pilot to Data reception
                            currentState = ReceiverState.RECEIVING
                            listener.onStateChanged(ReceiverState.RECEIVING, 95)
                            symbolAccumulator.clear()
                            bitStream.clear()
                            frameBits.clear()
                            isPreambleLocked = false
                            expectedTotalFrameBits = -1

                            for (i in 0 until samplesRead) {
                                symbolAccumulator.add(readBuffer[i])
                            }
                        }
                    }

                    ReceiverState.RECEIVING -> {
                        for (i in 0 until samplesRead) {
                            symbolAccumulator.add(readBuffer[i])
                        }

                        val samplesPerSymbol = config.samplesPerSymbol
                        while (symbolAccumulator.size >= samplesPerSymbol && isListening.get()) {
                            val symbolSamples = ShortArray(samplesPerSymbol)
                            for (s in 0 until samplesPerSymbol) {
                                symbolSamples[s] = symbolAccumulator.removeFirst()
                            }

                            val symbol = demodulator.demodulateSymbolWindow(symbolSamples, 0, samplesPerSymbol)
                            bitStream.add(symbol.bit)

                            if (!isPreambleLocked) {
                                // Continuously match trailing bits to preamble sync (0xAA, 0x55, 0x7E)
                                if (FSKDemodulator.matchesTrailingPreamble(bitStream, maxErrors = 2)) {
                                    isPreambleLocked = true
                                    frameBits.clear()
                                    expectedTotalFrameBits = -1
                                } else if (bitStream.size > 140) {
                                    // Reset on timeout without preamble lock
                                    currentState = ReceiverState.LISTENING
                                    listener.onStateChanged(ReceiverState.LISTENING, 0)
                                    symbolAccumulator.clear()
                                    bitStream.clear()
                                    signalDetector.reset()
                                    break
                                }
                            } else {
                                frameBits.add(symbol.bit)

                                // Once 48 bits (6 header bytes: Type, MsgId, Seq, Total, Len) are received, parse length
                                if (expectedTotalFrameBits == -1 && frameBits.size >= 48) {
                                    val headerBytes = demodulator.bitsToBytes(frameBits.take(48))
                                    val payloadLen = headerBytes[5].toInt() and 0xFF
                                    expectedTotalFrameBits = (6 + payloadLen + 2) * 8
                                }

                                if (expectedTotalFrameBits != -1 && frameBits.size >= expectedTotalFrameBits) {
                                    val packetBits = frameBits.take(expectedTotalFrameBits)
                                    val packetBytes = demodulator.bitsToBytes(packetBits)

                                    val type = packetBytes[0]
                                    val msgId = ((packetBytes[1].toInt() and 0xFF) shl 8) or (packetBytes[2].toInt() and 0xFF)
                                    val seq = packetBytes[3].toInt() and 0xFF
                                    val total = packetBytes[4].toInt() and 0xFF
                                    val len = packetBytes[5].toInt() and 0xFF

                                    if (6 + len + 2 <= packetBytes.size) {
                                        val payload = ByteArray(len)
                                        System.arraycopy(packetBytes, 6, payload, 0, len)
                                        val receivedCrc = ((packetBytes[6 + len].toInt() and 0xFF) shl 8) or (packetBytes[6 + len + 1].toInt() and 0xFF)

                                        val calculatedCrc = com.acoulink.protocol.PacketEncoder.calculatePacketCrc(
                                            type = type,
                                            messageId = msgId,
                                            sequenceNumber = seq,
                                            totalPackets = total,
                                            payload = payload
                                        )

                                        val isCorrupted = (calculatedCrc and 0xFFFF) != (receivedCrc and 0xFFFF)
                                        val packet = Packet(
                                            type = type,
                                            messageId = msgId,
                                            sequenceNumber = seq,
                                            totalPackets = total,
                                            payload = payload,
                                            crc16 = receivedCrc,
                                            isCorrupted = isCorrupted
                                        )

                                        listener.onPacketDecoded(packet)
                                    }

                                    // Return to listening state for following frames
                                    currentState = ReceiverState.LISTENING
                                    listener.onStateChanged(ReceiverState.LISTENING, 0)
                                    symbolAccumulator.clear()
                                    bitStream.clear()
                                    frameBits.clear()
                                    isPreambleLocked = false
                                    expectedTotalFrameBits = -1
                                    signalDetector.reset()
                                    break
                                }
                            }
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
