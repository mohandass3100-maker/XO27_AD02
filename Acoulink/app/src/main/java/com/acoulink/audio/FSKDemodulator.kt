package com.acoulink.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Result of demodulating a single acoustic symbol window.
 */
data class DemodulatedSymbol(
    val bit: Int,
    val markPower: Double,
    val spacePower: Double,
    val snr: Double,
    val isConfident: Boolean
)

/**
 * High-efficiency BFSK Demodulator utilizing the dual-frequency Goertzel algorithm.
 */
class FSKDemodulator(
    private val config: AudioConfig = AudioConfig()
) {
    /**
     * Computes the Goertzel magnitude/power at a specific target frequency over a window of samples.
     */
    fun computeGoertzelPower(
        samples: ShortArray,
        offset: Int,
        length: Int,
        targetFreq: Double
    ): Double {
        if (length <= 0 || offset + length > samples.size) return 0.0

        val sampleRate = config.sampleRate.toDouble()
        val k = ((length * targetFreq) / sampleRate).roundToInt()
        val omega = (2.0 * PI * k) / length
        val coeff = 2.0 * cos(omega)

        var sPrev = 0.0
        var sPrev2 = 0.0

        for (i in offset until (offset + length)) {
            val sampleNorm = samples[i] / 32768.0 // Normalized float sample [-1.0..1.0]
            val s = sampleNorm + coeff * sPrev - sPrev2
            sPrev2 = sPrev
            sPrev = s
        }

        val power = sPrev * sPrev + sPrev2 * sPrev2 - coeff * sPrev * sPrev2
        return if (power > 0.0) power else 0.0
    }

    /**
     * Demodulates a single symbol duration window by comparing power at Mark vs Space frequencies.
     */
    fun demodulateSymbolWindow(
        samples: ShortArray,
        offset: Int,
        length: Int = config.samplesPerSymbol
    ): DemodulatedSymbol {
        val markPower = computeGoertzelPower(samples, offset, length, config.markFreq)
        val spacePower = computeGoertzelPower(samples, offset, length, config.spaceFreq)

        val totalPower = markPower + spacePower
        val snr = if (totalPower > 0.0) {
            val maxP = maxOf(markPower, spacePower)
            val minP = minOf(markPower, spacePower).coerceAtLeast(1e-9)
            maxP / minP
        } else {
            1.0
        }

        val bit = if (markPower > spacePower) 1 else 0
        // Confident if one tone clearly dominates and total signal is above minimum threshold
        val isConfident = snr >= 1.5 && totalPower > 0.0001

        return DemodulatedSymbol(
            bit = bit,
            markPower = markPower,
            spacePower = spacePower,
            snr = snr,
            isConfident = isConfident
        )
    }

    /**
     * Converts a stream of detected bits into standard 8-bit bytes (MSB first).
     */
    fun bitsToBytes(bits: List<Int>): ByteArray {
        val byteCount = bits.size / 8
        val bytes = ByteArray(byteCount)

        for (i in 0 until byteCount) {
            var byteVal = 0
            for (bitIdx in 0 until 8) {
                val bit = bits[i * 8 + bitIdx]
                byteVal = (byteVal shl 1) or (bit and 0x01)
            }
            bytes[i] = byteVal.toByte()
        }
        return bytes
    }
}
