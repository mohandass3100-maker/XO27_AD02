package com.acoulink.protocol

import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * Encodes text messages and protocol control signals into framed byte packets.
 */
object PacketEncoder {

    /**
     * Splits a text or URL message into a sequence of numbered DATA packets with CRC-16 checksums.
     */
    fun createDataPackets(
        message: String,
        maxPayloadSize: Int = ProtocolConstants.DEFAULT_MAX_PAYLOAD_SIZE,
        messageId: Int = Random.nextInt(0x1000, 0xFFFF)
    ): List<Packet> {
        val utf8Bytes = message.toByteArray(Charsets.UTF_8)
        val totalPackets = if (utf8Bytes.isEmpty()) 1 else (utf8Bytes.size + maxPayloadSize - 1) / maxPayloadSize
        val packets = mutableListOf<Packet>()

        for (seq in 1..totalPackets) {
            val start = (seq - 1) * maxPayloadSize
            val end = (start + maxPayloadSize).coerceAtMost(utf8Bytes.size)
            val chunk = if (utf8Bytes.isNotEmpty()) utf8Bytes.copyOfRange(start, end) else ByteArray(0)

            // Compute CRC over header fields + payload
            val crc = calculatePacketCrc(
                type = ProtocolConstants.TYPE_DATA,
                messageId = messageId,
                sequenceNumber = seq,
                totalPackets = totalPackets,
                payload = chunk
            )

            packets.add(
                Packet(
                    type = ProtocolConstants.TYPE_DATA,
                    messageId = messageId,
                    sequenceNumber = seq,
                    totalPackets = totalPackets,
                    payload = chunk,
                    crc16 = crc,
                    isCorrupted = false
                )
            )
        }
        return packets
    }

    /**
     * Constructs a NACK packet requesting retransmission of specific sequence numbers.
     */
    fun createNackPacket(messageId: Int, missingSequences: List<Int>): Packet {
        val payload = ByteArray(missingSequences.size) { i -> missingSequences[i].toByte() }
        val crc = calculatePacketCrc(
            type = ProtocolConstants.TYPE_NACK,
            messageId = messageId,
            sequenceNumber = 0,
            totalPackets = 1,
            payload = payload
        )
        return Packet(
            type = ProtocolConstants.TYPE_NACK,
            messageId = messageId,
            sequenceNumber = 0,
            totalPackets = 1,
            payload = payload,
            crc16 = crc,
            isCorrupted = false
        )
    }

    /**
     * Constructs an ACK packet acknowledging complete message reception.
     */
    fun createAckPacket(messageId: Int): Packet {
        val payload = byteArrayOf(0x06) // ASCII ACK
        val crc = calculatePacketCrc(
            type = ProtocolConstants.TYPE_ACK,
            messageId = messageId,
            sequenceNumber = 0,
            totalPackets = 1,
            payload = payload
        )
        return Packet(
            type = ProtocolConstants.TYPE_ACK,
            messageId = messageId,
            sequenceNumber = 0,
            totalPackets = 1,
            payload = payload,
            crc16 = crc,
            isCorrupted = false
        )
    }

    /**
     * Serializes a Packet into a raw byte frame ready for acoustic modulation.
     * Frame format:
     * [PREAMBLE(3) | TYPE(1) | MSG_ID(2) | SEQ(1) | TOTAL(1) | LEN(1) | PAYLOAD(N) | CRC16(2)]
     */
    fun serializeFrame(packet: Packet): ByteArray {
        val out = ByteArrayOutputStream()
        
        // 1. Preamble sync bytes
        out.write(ProtocolConstants.PREAMBLE_SYNC_BYTES)

        // 2. Type (1 byte)
        out.write(packet.type.toInt() and 0xFF)

        // 3. Message ID (2 bytes, big-endian)
        out.write((packet.messageId ushr 8) and 0xFF)
        out.write(packet.messageId and 0xFF)

        // 4. Sequence Number (1 byte)
        out.write(packet.sequenceNumber and 0xFF)

        // 5. Total Packets (1 byte)
        out.write(packet.totalPackets and 0xFF)

        // 6. Payload Length (1 byte)
        out.write(packet.payloadLength and 0xFF)

        // 7. Payload bytes
        out.write(packet.payload)

        // 8. CRC-16 (2 bytes, big-endian)
        val calculatedCrc = calculatePacketCrc(
            type = packet.type,
            messageId = packet.messageId,
            sequenceNumber = packet.sequenceNumber,
            totalPackets = packet.totalPackets,
            payload = packet.payload
        )
        out.write((calculatedCrc ushr 8) and 0xFF)
        out.write(calculatedCrc and 0xFF)

        return out.toByteArray()
    }

    /**
     * Calculates the CRC-16 checksum for the header and payload data of a packet.
     */
    fun calculatePacketCrc(
        type: Byte,
        messageId: Int,
        sequenceNumber: Int,
        totalPackets: Int,
        payload: ByteArray
    ): Int {
        val headerAndData = ByteArray(6 + payload.size)
        headerAndData[0] = type
        headerAndData[1] = ((messageId ushr 8) and 0xFF).toByte()
        headerAndData[2] = (messageId and 0xFF).toByte()
        headerAndData[3] = (sequenceNumber and 0xFF).toByte()
        headerAndData[4] = (totalPackets and 0xFF).toByte()
        headerAndData[5] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, headerAndData, 6, payload.size)
        return CRC16.compute(headerAndData)
    }
}
