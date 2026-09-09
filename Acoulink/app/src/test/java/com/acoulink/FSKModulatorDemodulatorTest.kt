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
        val config = AudioConfig(symbolDurationMs = 30)
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
}
