package com.acoulink.protocol

/**
 * High-performance CRC-16-CCITT implementation for acoustic packet integrity checking.
 * Polynomial: 0x1021 (x^16 + x^12 + x^5 + 1)
 * Initial value: 0xFFFF
 */
object CRC16 {
    private const val POLYNOMIAL = 0x1021
    private const val INITIAL_VALUE = 0xFFFF
    
    // Precomputed lookup table for fast CRC-16 calculation
    private val TABLE = IntArray(256) { i ->
        var curr = i shl 8
        for (j in 0 until 8) {
            curr = if ((curr and 0x8000) != 0) {
                ((curr shl 1) xor POLYNOMIAL) and 0xFFFF
            } else {
                (curr shl 1) and 0xFFFF
            }
        }
        curr
    }

    /**
     * Computes the 16-bit CRC over the given byte array.
     */
    fun compute(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        var crc = INITIAL_VALUE
        for (i in offset until (offset + length)) {
            val byteVal = bytes[i].toInt() and 0xFF
            val tableIndex = ((crc ushr 8) xor byteVal) and 0xFF
            crc = ((crc shl 8) xor TABLE[tableIndex]) and 0xFFFF
        }
        return crc
    }

    /**
     * Validates whether the calculated CRC over the data bytes matches the expected CRC.
     */
    fun verify(bytes: ByteArray, expectedCrc: Int): Boolean {
        return compute(bytes) == (expectedCrc and 0xFFFF)
    }
}
