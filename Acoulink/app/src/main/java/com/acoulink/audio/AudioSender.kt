package com.acoulink.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.acoulink.protocol.Packet
import com.acoulink.protocol.ProtocolConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Listener for tracking acoustic transmission progress.
 */
interface TransmissionProgressListener {
    fun onPacketTransmitting(currentPacket: Int, totalPackets: Int, packet: Packet)
    fun onPacketTransmitted(currentPacket: Int, totalPackets: Int, packet: Packet)
    fun onTransmissionComplete(totalPackets: Int)
    fun onTransmissionStopped()
    fun onError(message: String)
}

/**
 * Manages AudioTrack playback of modulated BFSK acoustic packets.
 */
class AudioSender(
    private val config: AudioConfig = AudioConfig()
) {
    private val modulator = FSKModulator(config)
    private val isTransmitting = AtomicBoolean(false)
    private var activeAudioTrack: AudioTrack? = null

    /**
     * Broadcasts a list of packets sequentially over the device speaker.
     */
    suspend fun transmitPackets(
        packets: List<Packet>,
        listener: TransmissionProgressListener? = null
    ) = withContext(Dispatchers.IO) {
        if (packets.isEmpty()) {
            listener?.onTransmissionComplete(0)
            return@withContext
        }

        if (isTransmitting.getAndSet(true)) {
            listener?.onError("Transmission already in progress")
            return@withContext
        }

        var audioTrack: AudioTrack? = null
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                config.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(config.sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize.coerceAtLeast(8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            activeAudioTrack = audioTrack
            audioTrack.play()

            for ((index, packet) in packets.withIndex()) {
                if (!isTransmitting.get()) break

                listener?.onPacketTransmitting(index + 1, packets.size, packet)

                // 1. Modulate packet to PCM samples
                val pcmSamples = modulator.modulatePacket(packet)

                // 2. Stream into AudioTrack
                var written = 0
                while (written < pcmSamples.size && isTransmitting.get()) {
                    val count = audioTrack.write(
                        pcmSamples,
                        written,
                        pcmSamples.size - written,
                        AudioTrack.WRITE_BLOCKING
                    )
                    if (count > 0) {
                        written += count
                    } else {
                        break
                    }
                }

                listener?.onPacketTransmitted(index + 1, packets.size, packet)

                // Wait for hardware buffer to drain and provide guard interval
                if (index < packets.size - 1 && isTransmitting.get()) {
                    kotlinx.coroutines.delay(ProtocolConstants.GUARD_INTERVAL_MS.coerceAtLeast(150L))
                } else if (isTransmitting.get()) {
                    // Final packet: wait for speaker to finish playing trailing silence before releasing AudioTrack
                    kotlinx.coroutines.delay(350L)
                }
            }

            if (isTransmitting.get()) {
                listener?.onTransmissionComplete(packets.size)
            } else {
                listener?.onTransmissionStopped()
            }
        } catch (e: CancellationException) {
            listener?.onTransmissionStopped()
            throw e
        } catch (e: Exception) {
            listener?.onError("Audio transmission failed: ${e.message ?: "Unknown error"}")
        } finally {
            isTransmitting.set(false)
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            activeAudioTrack = null
        }
    }

    /**
     * Halts ongoing acoustic transmission immediately.
     */
    fun stop() {
        isTransmitting.set(false)
        try {
            activeAudioTrack?.pause()
            activeAudioTrack?.flush()
        } catch (_: Exception) {}
    }

    val isActive: Boolean
        get() = isTransmitting.get()
}
