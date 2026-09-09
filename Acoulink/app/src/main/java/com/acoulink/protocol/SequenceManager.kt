package com.acoulink.protocol

/**
 * Result of inspecting and processing an incoming packet's sequence number and CRC integrity.
 */
sealed class SequenceStatus {
    data class Accepted(val packet: Packet, val isDuplicate: Boolean) : SequenceStatus()
    data class Corrupted(val packet: Packet) : SequenceStatus()
    object IgnoredWrongSession : SequenceStatus()
}

/**
 * Manages sequence number tracking, detects missing packets, filters duplicates,
 * and tracks session boundaries for AcouLink receptions.
 */
class SequenceManager {
    var activeMessageId: Int? = null
        private set

    var totalPackets: Int = 0
        private set

    private val receivedValidPackets = mutableMapOf<Int, Packet>()
    private val corruptedSequences = mutableSetOf<Int>()

    /**
     * Processes an incoming decoded packet.
     */
    fun processPacket(packet: Packet): SequenceStatus {
        // Initialize or verify session MessageId
        if (activeMessageId == null) {
            activeMessageId = packet.messageId
            totalPackets = packet.totalPackets
        } else if (activeMessageId != packet.messageId) {
            // Received packet from a different session
            return SequenceStatus.IgnoredWrongSession
        }

        // Update total packets if needed
        if (packet.totalPackets > 0 && totalPackets == 0) {
            totalPackets = packet.totalPackets
        }

        if (packet.isCorrupted) {
            corruptedSequences.add(packet.sequenceNumber)
            return SequenceStatus.Corrupted(packet)
        }

        // Check for duplicate
        val isDuplicate = receivedValidPackets.containsKey(packet.sequenceNumber)
        receivedValidPackets[packet.sequenceNumber] = packet
        corruptedSequences.remove(packet.sequenceNumber)

        return SequenceStatus.Accepted(packet, isDuplicate)
    }

    /**
     * Identifies all sequence numbers between 1 and totalPackets that are missing or corrupted.
     */
    fun getMissingSequences(): List<Int> {
        if (totalPackets <= 0) return emptyList()
        val missing = mutableListOf<Int>()
        for (seq in 1..totalPackets) {
            if (!receivedValidPackets.containsKey(seq) || corruptedSequences.contains(seq)) {
                missing.add(seq)
            }
        }
        return missing
    }

    /**
     * Returns true if all expected packets (1..totalPackets) have been successfully received and validated.
     */
    fun isComplete(): Boolean {
        return totalPackets > 0 && receivedValidPackets.size == totalPackets && getMissingSequences().isEmpty()
    }

    /**
     * Returns valid packets sorted by their sequence number.
     */
    fun getOrderedPackets(): List<Packet> {
        return receivedValidPackets.values.sortedBy { it.sequenceNumber }
    }

    val validPacketCount: Int
        get() = receivedValidPackets.size

    fun reset() {
        activeMessageId = null
        totalPackets = 0
        receivedValidPackets.clear()
        corruptedSequences.clear()
    }
}
