package com.acoulink.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Direction of message communication.
 */
enum class MessageDirection {
    SENT,
    RECEIVED
}

/**
 * Room database entity storing metadata and content of an acoustic message session.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: String,
    val content: String,
    val type: String, // "TEXT" or "URL"
    val direction: MessageDirection,
    val timestamp: Long = System.currentTimeMillis(),
    val packetCount: Int,
    val validPacketCount: Int,
    val retransmittedPacketCount: Int = 0,
    val crcStatus: String = "VERIFIED", // "VERIFIED", "CORRUPTED"
    val recoveryStatus: String = "NONE", // "NONE", "RECOVERED", "FAILED"
    val transmissionStatus: String = "SUCCESS" // "SUCCESS", "PARTIAL", "FAILED"
)
