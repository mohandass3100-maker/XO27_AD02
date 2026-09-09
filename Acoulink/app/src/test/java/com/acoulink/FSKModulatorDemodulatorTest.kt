package com.acoulink

import com.acoulink.audio.AudioConfig
import com.acoulink.audio.FSKDemodulator
import com.acoulink.audio.FSKModulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FSKModulatorDemodulatorTest {

    @Test
    fun testFSKGoertzelToneDiscrimination() {
        val config = AudioConfig(symbolDurationMs = 40)
        val modulator = FSKModulator(config)
        val demodulator = FSKDemodulator(config)

        // Synthesize single Mark (1) and Space (0) bursts
        val testBytes = byteArrayOf(0b10100000.toByte())
        val pcm = modulator.modulateBytes(testBytes)

        assertTrue("Generated PCM buffer should contain samples", pcm.isNotEmpty())

        // Demodulate individual symbol window
        // Skip pilot tone + guard samples
        val dataOffset = config.pilotSampleCount + config.guardSampleCount
        val symbolLen = config.samplesPerSymbol

        // First bit is 1
        val sym1 = demodulator.demodulateSymbolWindow(pcm, dataOffset, symbolLen)
        assertEquals("First bit should demodulate to 1", 1, sym1.bit)
        assertTrue("Mark power should dominate for bit 1", sym1.markPower > sym1.spacePower)

        // Second bit is 0
        val sym2 = demodulator.demodulateSymbolWindow(pcm, dataOffset + symbolLen, symbolLen)
        assertEquals("Second bit should demodulate to 0", 0, sym2.bit)
        assertTrue("Space power should dominate for bit 0", sym2.spacePower > sym2.markPower)
    }

    @Test
    fun testPreambleBitPatternMatching() {
        // Exact preamble bits
        val preamble = FSKDemodulator.PREAMBLE_BITS
        assertTrue(FSKDemodulator.matchesTrailingPreamble(preamble, maxErrors = 0))

        // Preamble with 1 bit error (noise tolerance)
        val withOneError = preamble.toMutableList()
        withOneError[5] = if (withOneError[5] == 1) 0 else 1
        assertTrue(FSKDemodulator.matchesTrailingPreamble(withOneError, maxErrors = 1))

        // Preamble with leading noise bits
        val withNoise = listOf(0, 1, 1, 0, 0) + preamble
        assertTrue(FSKDemodulator.matchesTrailingPreamble(withNoise, maxErrors = 0))
    }

    @Test
    fun testEndToEndModulationAndDemodulation() {
        val config = AudioConfig(symbolDurationMs = 40)
        val modulator = FSKModulator(config)
        val demodulator = FSKDemodulator(config)

        val originalBytes = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x7E.toByte(), 0x01, 0x05, 0x42)
        val pcm = modulator.modulateBytes(originalBytes)

        val dataOffset = config.pilotSampleCount + config.guardSampleCount
        val symbolLen = config.samplesPerSymbol
        val totalBits = originalBytes.size * 8
        val recoveredBits = mutableListOf<Int>()

        for (i in 0 until totalBits) {
            val offset = dataOffset + (i * symbolLen)
            val sym = demodulator.demodulateSymbolWindow(pcm, offset, symbolLen)
            recoveredBits.add(sym.bit)
        }

        val recoveredBytes = demodulator.bitsToBytes(recoveredBits)
        assertEquals("Recovered byte count should match original", originalBytes.size, recoveredBytes.size)
        for (i in originalBytes.indices) {
            assertEquals("Byte at index $i should match exactly", originalBytes[i], recoveredBytes[i])
        }
    }
}
