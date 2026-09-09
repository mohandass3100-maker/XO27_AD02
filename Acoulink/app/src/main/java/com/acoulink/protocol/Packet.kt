package com.acoulink.protocol

/**
 * Represents a discrete acoustic communication frame in AcouLink.
 *
 * @property type Type of packet: DATA, ACK, or NACK.
 * @property messageId Unique 16-bit identifier for the message session (e.g., 0xAC12).
 * @property sequenceNumber Sequence number of this packet (1-based, 1..totalPackets).
 * @property totalPackets Total number of packets comprising the complete message.
 * @property payload Raw byte payload (UTF-8 encoded string chunk or recovery sequence list).
 * @property crc16 16-bit CRC checksum calculated over [type, messageId, sequenceNumber, totalPackets, payloadLength, payload].
 * @property isCorrupted Flag indicating whether CRC verification failed during reception.
 */
data class Packet(
    val type: Byte = ProtocolConstants.TYPE_DATA,
    val messageId: Int, // 16-bit integer (0x0000..0xFFFF)
    val sequenceNumber: Int, // 1..totalPackets
    val totalPackets: Int,
    val payload: ByteArray,
    val crc16: Int = 0,
    val isCorrupted: Boolean = false
) {
    val payloadLength: Int
        get() = payload.size

    val formattedMessageId: String
        get() = String.format("AC%04X", messageId and 0xFFFF)

    val formattedSequence: String
        get() = String.format("%02d", sequenceNumber)

    val formattedTotal: String
        get() = String.format("%02d", totalPackets)

    val payloadAsUtf8: String
        get() = try {
            String(payload, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (type != other.type) return false
        if (messageId != other.messageId) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (totalPackets != other.totalPackets) return false
        if (!payload.contentEquals(other.payload)) return false
        if (crc16 != other.crc16) return false
        if (isCorrupted != other.isCorrupted) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.toInt()
        result = 31 * result + messageId
        result = 31 * result + sequenceNumber
        result = 31 * result + totalPackets
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + crc16
        result = 31 * result + isCorrupted.hashCode()
        return result
    }

    val isData: Boolean get() = type == ProtocolConstants.TYPE_DATA
    val isBeacon: Boolean get() = type == ProtocolConstants.TYPE_BEACON
    val isRequest: Boolean get() = type == ProtocolConstants.TYPE_REQUEST
    val isNack: Boolean get() = type == ProtocolConstants.TYPE_NACK
    val isAck: Boolean get() = type == ProtocolConstants.TYPE_ACK

    override fun toString(): String {
        val typeStr = when (type) {
            ProtocolConstants.TYPE_DATA -> "DATA"
            ProtocolConstants.TYPE_ACK -> "ACK"
            ProtocolConstants.TYPE_NACK -> "NACK"
            ProtocolConstants.TYPE_BEACON -> "BEACON"
            ProtocolConstants.TYPE_REQUEST -> "REQUEST"
            else -> "UNKNOWN(0x${type.toString(16)})"
        }
        return "Packet($typeStr, id=$formattedMessageId, seq=$formattedSequence/$formattedTotal, len=$payloadLength, crc=0x${Integer.toHexString(crc16).uppercase()}, corrupted=$isCorrupted)"
    }
}
