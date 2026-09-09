package com.acoulink

import com.acoulink.protocol.MessageAssembler
import com.acoulink.protocol.PacketEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAssemblerTest {

    @Test
    fun testUrlDetection() {
        assertTrue(MessageAssembler.isUrl("https://example.com/info"))
        assertTrue(MessageAssembler.isUrl("http://acoulink.dev"))
        assertTrue(MessageAssembler.isUrl("www.github.com"))
    }

    @Test
    fun testReassembleMessage() {
        val original = "Exam starts at 10:00 AM sharp in Hall C. https://exam.edu"
        val packets = PacketEncoder.createDataPackets(original, maxPayloadSize = 12, messageId = 0x5555)

        val assembled = MessageAssembler.assemble(packets, retransmittedCount = 1)
        assertNotNull(assembled)
        assertEquals(original, assembled?.content)
        assertEquals(packets.size, assembled?.packetCount)
        assertEquals(1, assembled?.retransmittedCount)
        assertTrue(assembled?.crcVerified == true)
    }
}
