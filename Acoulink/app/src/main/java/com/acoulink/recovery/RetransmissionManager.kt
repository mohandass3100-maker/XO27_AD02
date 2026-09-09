package com.acoulink.recovery

import com.acoulink.protocol.Packet
import com.acoulink.protocol.ProtocolConstants

/**
 * Manages sender-side packet buffering, retransmission scheduling, and retry thresholds.
 */
class RetransmissionManager(
    private val maxRetries: Int = ProtocolConstants.DEFAULT_MAX_RETRIES
) {
    private val packetCache = mutableMapOf<Int, Packet>()
    private var retryCount = 0
    private var totalRetransmittedCount = 0

    /**
     * Initializes the manager with the full list of broadcast packets.
     */
    fun initialize(packets: List<Packet>) {
        packetCache.clear()
        retryCount = 0
        totalRetransmittedCount = 0
        for (packet in packets) {
            packetCache[packet.sequenceNumber] = packet
        }
    }

    /**
     * Given a list of requested missing sequence numbers from a NACK,
     * returns the corresponding cached packets to retransmit, if within retry limits.
     */
    fun getPacketsForRetransmission(missingSequences: List<Int>): List<Packet> {
        if (retryCount >= maxRetries) {
            return emptyList()
        }

        val packetsToResend = mutableListOf<Packet>()
        for (seq in missingSequences) {
            packetCache[seq]?.let { packetsToResend.add(it) }
        }

        if (packetsToResend.isNotEmpty()) {
            retryCount++
            totalRetransmittedCount += packetsToResend.size
        }

        return packetsToResend
    }

    val canRetry: Boolean
        get() = retryCount < maxRetries

    val currentRetryRound: Int
        get() = retryCount

    val totalRetransmissions: Int
        get() = totalRetransmittedCount

    fun reset() {
        packetCache.clear()
        retryCount = 0
        totalRetransmittedCount = 0
    }
}
