package com.acoulink.protocol

import java.io.ByteArrayInputStream

/**
 * Parses raw demodulated byte streams, identifies frame boundaries via preamble sync,
 * extracts packet fields, and performs CRC-16 verification.
 */
class PacketDecoder {

    private val buffer = mutableListOf<Byte>()

    /**
     * Feeds incoming demodulated bytes into the internal parsing buffer
     * and returns any fully received packets found.
     */
    fun feedBytes(newBytes: ByteArray): List<Packet> {
        for (b in newBytes) {
            buffer.add(b)
        }
        return extractPackets()
    }

    /**
     * Scans the buffer for valid frames.
     */
    fun extractPackets(): List<Packet> {
        val packets = mutableListOf<Packet>()
        val syncBytes = ProtocolConstants.PREAMBLE_SYNC_BYTES

        while (buffer.size >= syncBytes.size + 8) { // Minimum possible frame size (0-len payload)
            // 1. Locate sync bytes
            val syncIndex = findSyncIndex()
            if (syncIndex == -1) {
                // Keep only the last (syncBytes.size - 1) bytes in case sync is split across chunks
                if (buffer.size > syncBytes.size) {
                    val dropCount = buffer.size - (syncBytes.size - 1)
                    repeat(dropCount) { buffer.removeAt(0) }
                }
                break
            }

            // Drop any noise bytes before sync
            if (syncIndex > 0) {
                repeat(syncIndex) { buffer.removeAt(0) }
            }

            // Frame starts at index 0 now
            // We need at least: Sync(3) + Type(1) + MsgId(2) + Seq(1) + Total(1) + Len(1) + CRC(2) = 11 bytes
            if (buffer.size < 11) {
                break // Wait for more bytes
            }

            val payloadLength = buffer[8].toInt() and 0xFF
            val totalFrameLength = 3 + 1 + 2 + 1 + 1 + 1 + payloadLength + 2

            if (buffer.size < totalFrameLength) {
                // Incomplete frame; wait for remaining bytes
                break
            }

            // Extract the candidate frame bytes
            val frameBytes = ByteArray(totalFrameLength)
            for (i in 0 until totalFrameLength) {
                frameBytes[i] = buffer.removeAt(0)
            }

            // Parse packet fields
            val packet = decodeSingleFrame(frameBytes)
            if (packet != null) {
                packets.add(packet)
            }
        }
        return packets
    }

    /**
     * Decodes an isolated byte array representing a single complete frame.
     */
    fun decodeSingleFrame(frameBytes: ByteArray): Packet? {
        if (frameBytes.size < 11) return null

        val sync = ProtocolConstants.PREAMBLE_SYNC_BYTES
        if (frameBytes[0] != sync[0] || frameBytes[1] != sync[1] || frameBytes[2] != sync[2]) {
            return null
        }

        var offset = 3
        val type = frameBytes[offset++]
        val msgId = ((frameBytes[offset++].toInt() and 0xFF) shl 8) or (frameBytes[offset++].toInt() and 0xFF)
        val seq = frameBytes[offset++].toInt() and 0xFF
        val total = frameBytes[offset++].toInt() and 0xFF
        val payloadLen = frameBytes[offset++].toInt() and 0xFF

        if (offset + payloadLen + 2 > frameBytes.size) {
            return null
        }

        val payload = ByteArray(payloadLen)
        System.arraycopy(frameBytes, offset, payload, 0, payloadLen)
        offset += payloadLen

        val receivedCrc = ((frameBytes[offset++].toInt() and 0xFF) shl 8) or (frameBytes[offset].toInt() and 0xFF)

        // CRC check
        val computedCrc = PacketEncoder.calculatePacketCrc(
            type = type,
            messageId = msgId,
            sequenceNumber = seq,
            totalPackets = total,
            payload = payload
        )

        val isValid = (computedCrc and 0xFFFF) == (receivedCrc and 0xFFFF)

        return Packet(
            type = type,
            messageId = msgId,
            sequenceNumber = seq,
            totalPackets = total,
            payload = payload,
            crc16 = receivedCrc,
            isCorrupted = !isValid
        )
    }

    private fun findSyncIndex(): Int {
        val sync = ProtocolConstants.PREAMBLE_SYNC_BYTES
        for (i in 0..(buffer.size - sync.size)) {
            if (buffer[i] == sync[0] && buffer[i + 1] == sync[1] && buffer[i + 2] == sync[2]) {
                return i
            }
        }
        return -1
    }

    fun reset() {
        buffer.clear()
    }
}
