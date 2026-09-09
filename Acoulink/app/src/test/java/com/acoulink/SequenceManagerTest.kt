package com.acoulink

import com.acoulink.protocol.Packet
import com.acoulink.protocol.ProtocolConstants
import com.acoulink.protocol.SequenceManager
import com.acoulink.protocol.SequenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceManagerTest {

    @Test
    fun testDetectMissingPackets() {
        val manager = SequenceManager()

        val p1 = Packet(ProtocolConstants.TYPE_DATA, 0x1234, sequenceNumber = 1, totalPackets = 4, payload = byteArrayOf(1))
        val p2 = Packet(ProtocolConstants.TYPE_DATA, 0x1234, sequenceNumber = 2, totalPackets = 4, payload = byteArrayOf(2))
        // Packet 3 dropped
        val p4 = Packet(ProtocolConstants.TYPE_DATA, 0x1234, sequenceNumber = 4, totalPackets = 4, payload = byteArrayOf(4))

        manager.processPacket(p1)
        manager.processPacket(p2)
        manager.processPacket(p4)

        assertFalse("Message is not complete when packet 3 is missing", manager.isComplete())
        val missing = manager.getMissingSequences()

        assertEquals(1, missing.size)
        assertEquals(3, missing[0])

        // When packet 3 arrives, message becomes complete
        val p3 = Packet(ProtocolConstants.TYPE_DATA, 0x1234, sequenceNumber = 3, totalPackets = 4, payload = byteArrayOf(3))
        manager.processPacket(p3)

        assertTrue("Message should now be complete", manager.isComplete())
        assertTrue("No missing sequences should remain", manager.getMissingSequences().isEmpty())
    }

    @Test
    fun testDuplicatePacketsFiltered() {
        val manager = SequenceManager()

        val p1 = Packet(ProtocolConstants.TYPE_DATA, 0x1234, sequenceNumber = 1, totalPackets = 2, payload = byteArrayOf(1))
        val status1 = manager.processPacket(p1)
        assertTrue(status1 is SequenceStatus.Accepted && !status1.isDuplicate)

        // Duplicate delivery
        val status2 = manager.processPacket(p1)
        assertTrue(status2 is SequenceStatus.Accepted && status2.isDuplicate)

        assertEquals(1, manager.validPacketCount)
    }
}
