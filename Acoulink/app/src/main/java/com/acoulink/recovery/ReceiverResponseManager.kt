package com.acoulink.recovery

import com.acoulink.protocol.ProtocolConstants
import kotlin.random.Random

/**
 * Coordinates slotted transmission scheduling for one-to-many receiver responses
 * to prevent acoustic collision when multiple devices reply with NACK/ACK.
 */
class ReceiverResponseManager(
    private val totalSlots: Int = 4,
    private val slotDurationMs: Long = ProtocolConstants.RESPONSE_SLOT_DURATION_MS
) {
    /**
     * Calculates the millisecond delay before a receiver transmits its response.
     * Combines an assigned or randomized slot with small pseudo-random jitter.
     */
    fun calculateSlotDelay(assignedSlot: Int? = null): Long {
        val slot = assignedSlot ?: Random.nextInt(0, totalSlots)
        val jitter = Random.nextLong(10, 60) // 10ms - 60ms acoustic channel jitter
        return (slot * slotDurationMs) + jitter
    }

    /**
     * Total window time during which the sender should listen for responses.
     */
    val totalResponseWindowMs: Long
        get() = (totalSlots * slotDurationMs) + 200L
}
