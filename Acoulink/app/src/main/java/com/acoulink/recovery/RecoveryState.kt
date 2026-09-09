package com.acoulink.recovery

/**
 * Represents the detailed lifecycle stages of the acoustic packet recovery process.
 */
sealed class RecoveryState {
    object Idle : RecoveryState()

    data class MissingDetected(
        val missingSequences: List<Int>,
        val totalPackets: Int
    ) : RecoveryState()

    data class SendingNack(
        val missingSequences: List<Int>,
        val slotIndex: Int
    ) : RecoveryState()

    data class AwaitingRetransmission(
        val missingSequences: List<Int>,
        val currentRound: Int,
        val maxRounds: Int
    ) : RecoveryState()

    data class RetransmissionReceived(
        val sequenceNumber: Int,
        val remainingMissing: List<Int>
    ) : RecoveryState()

    data class Recovered(
        val recoveredPacketsCount: Int
    ) : RecoveryState()

    data class Failed(
        val reason: String,
        val missingSequences: List<Int>
    ) : RecoveryState()
}
