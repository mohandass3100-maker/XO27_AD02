package com.acoulink.audio

import com.acoulink.protocol.Packet
import com.acoulink.protocol.PacketEncoder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Continuous-Phase Frequency-Shift Keying (CPFSK) modulator.
 * Synthesizes 16-bit PCM audio samples representing serialized packet bits.
 */
class FSKModulator(
    private val config: AudioConfig = AudioConfig()
) {
    /**
     * Modulates a [Packet] into a complete 16-bit PCM mono audio sample buffer.
     * Sequence: [Pilot Tone] -> [Guard Gap] -> [Preamble & Frame Bits] -> [Post Guard Gap]
     */
    fun modulatePacket(packet: Packet): ShortArray {
        val frameBytes = PacketEncoder.serializeFrame(packet)
        return modulateBytes(frameBytes)
    }

    /**
     * Modulates raw byte frames into 16-bit PCM samples with a pilot tone preamble.
     */
    fun modulateBytes(bytes: ByteArray): ShortArray {
        val sampleRate = config.sampleRate.toDouble()
        val samplesPerSymbol = config.samplesPerSymbol
        val pilotSamples = config.pilotSampleCount
        val guardSamples = config.guardSampleCount
        val totalBits = bytes.size * 8

        val totalSamples = pilotSamples + guardSamples + (totalBits * samplesPerSymbol) + guardSamples
        val pcm = ShortArray(totalSamples)

        var sampleIndex = 0
        var phase = 0.0
        val maxAmplitude = (Short.MAX_VALUE * config.amplitude).toInt()

        // 1. Synthesize Pilot Tone
        val pilotFreq = config.pilotFreq
        val pilotPhaseIncrement = 2.0 * PI * pilotFreq / sampleRate
        val rampSamples = (samplesPerSymbol / 2).coerceAtLeast(16)

        for (i in 0 until pilotSamples) {
            // Apply soft ramp-in and ramp-out to pilot tone
            val rampEnvelope = calculateEnvelope(i, pilotSamples, rampSamples)
            val sampleVal = (sin(phase) * maxAmplitude * rampEnvelope).toInt()
            pcm[sampleIndex++] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            phase = (phase + pilotPhaseIncrement) % (2.0 * PI)
        }

        // 2. Guard silence
        for (i in 0 until guardSamples) {
            pcm[sampleIndex++] = 0
        }

        // 3. Modulate Data Bits (Continuous Phase)
        for (b in bytes) {
            val byteVal = b.toInt() and 0xFF
            for (bitPos in 7 downTo 0) {
                val bit = (byteVal ushr bitPos) and 0x01
                val freq = if (bit == 1) config.markFreq else config.spaceFreq
                val phaseIncrement = 2.0 * PI * freq / sampleRate

                for (s in 0 until samplesPerSymbol) {
                    val sampleVal = (sin(phase) * maxAmplitude).toInt()
                    pcm[sampleIndex++] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    phase = (phase + phaseIncrement) % (2.0 * PI)
                }
            }
        }

        // 4. Post-guard silence
        for (i in 0 until guardSamples) {
            if (sampleIndex < pcm.size) {
                pcm[sampleIndex++] = 0
            }
        }

        return pcm
    }

    /**
     * Computes a smooth raised-cosine envelope factor [0.0..1.0] for gentle ramp-up and ramp-down.
     */
    private fun calculateEnvelope(index: Int, total: Int, rampLen: Int): Double {
        return when {
            index < rampLen -> 0.5 * (1.0 - cos(PI * index / rampLen))
            index >= (total - rampLen) -> 0.5 * (1.0 - cos(PI * (total - index) / rampLen))
            else -> 1.0
        }
    }
}
