package com.acoulink.recovery

import com.acoulink.protocol.Packet
import com.acoulink.protocol.PacketEncoder
import com.acoulink.protocol.ProtocolConstants

/**
 * Coordinates negative acknowledgments (NACK), missing packet encoding, and decoding.
 */
class NackManager {

    /**
     * Builds an acoustic NACK packet specifying the sequence numbers that must be retransmitted.
     */
    fun createNack(messageId: Int, missingSequences: List<Int>): Packet {
        return PacketEncoder.createNackPacket(messageId, missingSequences)
    }

    /**
     * Extracts missing sequence numbers from a received NACK packet payload.
     */
    fun extractMissingSequences(packet: Packet): List<Int> {
        if (packet.type != ProtocolConstants.TYPE_NACK || packet.isCorrupted) {
            return emptyList()
        }
        val missing = mutableListOf<Int>()
        for (b in packet.payload) {
            val seq = b.toInt() and 0xFF
            if (seq > 0) {
                missing.add(seq)
            }
        }
        return missing
    }

    /**
     * Checks if the packet is a valid NACK packet for the active message session.
     */
    fun isNackForSession(packet: Packet, expectedMessageId: Int): Boolean {
        return packet.type == ProtocolConstants.TYPE_NACK &&
                !packet.isCorrupted &&
                packet.messageId == expectedMessageId
    }
}
