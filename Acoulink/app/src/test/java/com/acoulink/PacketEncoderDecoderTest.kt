package com.acoulink

import com.acoulink.protocol.PacketDecoder
import com.acoulink.protocol.PacketEncoder
import com.acoulink.protocol.ProtocolConstants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketEncoderDecoderTest {

    @Test
    fun testPacketEncodingAndDecodingRoundTrip() {
        val originalText = "Meeting at 10 AM. https://example.com"
        val packets = PacketEncoder.createDataPackets(originalText, maxPayloadSize = 16, messageId = 0xAC12)

        assertTrue("Should produce multiple packets for 16-byte payload chunks", packets.size > 1)

        val decoder = PacketDecoder()

        for (packet in packets) {
            val frameBytes = PacketEncoder.serializeFrame(packet)
            val decodedList = decoder.feedBytes(frameBytes)

            assertEquals("Each serialized frame should produce 1 decoded packet", 1, decodedList.size)
            val decoded = decodedList[0]

            assertEquals(packet.messageId, decoded.messageId)
            assertEquals(packet.sequenceNumber, decoded.sequenceNumber)
            assertEquals(packet.totalPackets, decoded.totalPackets)
            assertArrayEquals(packet.payload, decoded.payload)
            assertFalse("CRC check must pass on intact frame", decoded.isCorrupted)
        }
    }

    @Test
    fun testCorruptedFrameCrcFails() {
        val originalText = "Sensitive Payload Data"
        val packets = PacketEncoder.createDataPackets(originalText, maxPayloadSize = 32, messageId = 0xBEEF)
        val frameBytes = PacketEncoder.serializeFrame(packets[0])

        // Corrupt a byte inside payload
        frameBytes[10] = (frameBytes[10].toInt() xor 0xFF).toByte()

        val decoder = PacketDecoder()
        val decodedList = decoder.feedBytes(frameBytes)

        assertEquals(1, decodedList.size)
        assertTrue("Corrupted byte should cause isCorrupted to be true", decodedList[0].isCorrupted)
    }

    @Test
    fun testNackAndAckPacketEncoding() {
        val nack = PacketEncoder.createNackPacket(messageId = 0x1234, missingSequences = listOf(3, 7))
        assertEquals(ProtocolConstants.TYPE_NACK, nack.type)
        assertEquals(2, nack.payload.size)

        val frame = PacketEncoder.serializeFrame(nack)
        val decoder = PacketDecoder()
        val decoded = decoder.feedBytes(frame)[0]

        assertEquals(ProtocolConstants.TYPE_NACK, decoded.type)
        assertEquals(0x1234, decoded.messageId)
        assertFalse(decoded.isCorrupted)
    }
}
