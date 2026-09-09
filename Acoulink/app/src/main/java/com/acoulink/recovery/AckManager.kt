package com.acoulink.recovery

import com.acoulink.protocol.Packet
import com.acoulink.protocol.PacketEncoder
import com.acoulink.protocol.ProtocolConstants

/**
 * Manages positive acknowledgments (ACK) for acoustic communication sessions.
 */
class AckManager {

    /**
     * Builds an acoustic ACK packet for the specified messageId.
     */
    fun createAck(messageId: Int): Packet {
        return PacketEncoder.createAckPacket(messageId)
    }

    /**
     * Verifies if a received packet is a valid, uncorrupted ACK packet for the current session.
     */
    fun isAckForSession(packet: Packet, expectedMessageId: Int): Boolean {
        return packet.type == ProtocolConstants.TYPE_ACK &&
                !packet.isCorrupted &&
                packet.messageId == expectedMessageId
    }
}
