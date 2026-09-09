package com.acoulink

import com.acoulink.protocol.CRC16
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CRC16Test {

    @Test
    fun testKnownCRC16Vectors() {
        // Test standard ASCII string "123456789"
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = CRC16.compute(data)

        // CRC-16-CCITT (poly 0x1021, init 0xFFFF) for "123456789" is 0x29B1
        assertEquals(0x29B1, crc)
        assertTrue(CRC16.verify(data, 0x29B1))
    }

    @Test
    fun testCrcCorruptionDetection() {
        val originalData = "AcouLink Acoustic Protocol".toByteArray(Charsets.UTF_8)
        val validCrc = CRC16.compute(originalData)

        // Ensure validity on original
        assertTrue(CRC16.verify(originalData, validCrc))

        // Flip 1 bit in corrupted copy
        val corrupted = originalData.copyOf()
        corrupted[3] = (corrupted[3].toInt() xor 0x01).toByte()

        assertFalse(CRC16.verify(corrupted, validCrc))
    }
}
