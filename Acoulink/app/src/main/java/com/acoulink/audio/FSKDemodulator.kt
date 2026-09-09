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
     * Computes the normalized Goertzel magnitude/power at a specific target frequency over a window of samples.
     * Normalized by window length squared to provide scale-invariant energy comparisons.
     */
    fun computeGoertzelPower(
        samples: ShortArray,
        offset: Int,
        length: Int,
        targetFreq: Double
    ): Double {
        if (length <= 0 || offset < 0 || offset + length > samples.size) return 0.0

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

        val rawPower = sPrev * sPrev + sPrev2 * sPrev2 - coeff * sPrev * sPrev2
        val normPower = if (rawPower > 0.0) rawPower / (length.toDouble() * length.toDouble()) else 0.0
        return normPower
    }

    /**
     * Demodulates a single symbol duration window by comparing power at Mark vs Space frequencies.
     * Evaluates the center 60% of the window to reject edge reverberation and inter-symbol interference.
     */
    fun demodulateSymbolWindow(
        samples: ShortArray,
        offset: Int,
        length: Int = config.samplesPerSymbol
    ): DemodulatedSymbol {
        val guardFraction = 0.20
        val skip = (length * guardFraction).toInt()
        val evalOffset = offset + skip
        val evalLength = (length * (1.0 - 2 * guardFraction)).toInt()

        val markPower = computeGoertzelPower(samples, evalOffset, evalLength, config.markFreq)
        val spacePower = computeGoertzelPower(samples, evalOffset, evalLength, config.spaceFreq)

        val totalPower = markPower + spacePower
        val snr = if (totalPower > 0.0) {
            val maxP = maxOf(markPower, spacePower)
            val minP = minOf(markPower, spacePower).coerceAtLeast(1e-9)
            maxP / minP
        } else {
            1.0
        }

        val bit = if (markPower > spacePower) 1 else 0
        // Confident if one tone clearly dominates and total signal is above ambient noise floor
        val isConfident = snr >= 1.25 && totalPower > 0.00002

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

    /**
     * Converts an array of bytes into an array of individual bits (MSB first).
     */
    fun bytesToBits(bytes: ByteArray): List<Int> {
        val bits = ArrayList<Int>(bytes.size * 8)
        for (b in bytes) {
            val byteVal = b.toInt() and 0xFF
            for (bitPos in 7 downTo 0) {
                bits.add((byteVal ushr bitPos) and 0x01)
            }
        }
        return bits
    }

    companion object {
        /**
         * Expected 24-bit preamble synchronization pattern (0xAA, 0x55, 0x7E).
         */
        val PREAMBLE_BITS = listOf(
            1, 0, 1, 0, 1, 0, 1, 0, // 0xAA
            0, 1, 0, 1, 0, 1, 0, 1, // 0x55
            0, 1, 1, 1, 1, 1, 1, 0  // 0x7E
        )

        /**
         * Computes the Hamming distance (number of bit mismatches) between two bit sequences.
         */
        fun hammingDistance(a: List<Int>, aOffset: Int, b: List<Int>, length: Int): Int {
            var diff = 0
            for (i in 0 until length) {
                if (a[aOffset + i] != b[i]) {
                    diff++
                }
            }
            return diff
        }

        /**
         * Checks if the trailing 24 bits of [bits] match the preamble within [maxErrors].
         */
        fun matchesTrailingPreamble(bits: List<Int>, maxErrors: Int = 1): Boolean {
            if (bits.size < PREAMBLE_BITS.size) return false
            val startIdx = bits.size - PREAMBLE_BITS.size
            return hammingDistance(bits, startIdx, PREAMBLE_BITS, PREAMBLE_BITS.size) <= maxErrors
        }
    }
}
