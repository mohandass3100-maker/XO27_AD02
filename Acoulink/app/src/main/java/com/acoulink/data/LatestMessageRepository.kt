package com.acoulink.data

import android.content.Context
import android.content.SharedPreferences
import com.acoulink.protocol.Packet
import com.acoulink.protocol.PacketEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data model for the cached latest broadcast message.
 */
data class CachedMessage(
    val messageId: Int,
    val content: String,
    val isUrl: Boolean,
    val totalPackets: Int,
    val packets: List<Packet>,
    val timestamp: Long
)

/**
 * Repository responsible for caching the latest transmitted message to support
 * Surprise Challenge 2 (Dynamic Group acoustic discovery and automated retransmission).
 */
class LatestMessageRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("acoulink_latest_cache", Context.MODE_PRIVATE)

    private var cachedMessage: CachedMessage? = null

    private val _isBeaconActive = MutableStateFlow(false)
    val isBeaconActive: StateFlow<Boolean> = _isBeaconActive.asStateFlow()

    init {
        // Restore cached message metadata from preferences if available
        val savedId = prefs.getInt(KEY_MESSAGE_ID, -1)
        val savedContent = prefs.getString(KEY_CONTENT, null)
        val savedIsUrl = prefs.getBoolean(KEY_IS_URL, false)
        val savedTimestamp = prefs.getLong(KEY_TIMESTAMP, 0L)

        if (savedId != -1 && !savedContent.isNullOrEmpty()) {
            val restoredPackets = PacketEncoder.createDataPackets(
                message = savedContent,
                messageId = savedId
            )
            cachedMessage = CachedMessage(
                messageId = savedId,
                content = savedContent,
                isUrl = savedIsUrl,
                totalPackets = restoredPackets.size,
                packets = restoredPackets,
                timestamp = savedTimestamp
            )
        }
    }

    /**
     * Caches the most recently broadcast message both in-memory and persistently.
     */
    fun storeLatestBroadcast(
        messageId: Int,
        content: String,
        isUrl: Boolean,
        packets: List<Packet>
    ) {
        val timestamp = System.currentTimeMillis()
        cachedMessage = CachedMessage(
            messageId = messageId,
            content = content,
            isUrl = isUrl,
            totalPackets = packets.size,
            packets = packets,
            timestamp = timestamp
        )

        prefs.edit()
            .putInt(KEY_MESSAGE_ID, messageId)
            .putString(KEY_CONTENT, content)
            .putBoolean(KEY_IS_URL, isUrl)
            .putLong(KEY_TIMESTAMP, timestamp)
            .apply()
    }

    fun getLatestBroadcast(): CachedMessage? = cachedMessage

    fun hasLatestBroadcast(): Boolean = cachedMessage != null

    fun setBeaconActive(active: Boolean) {
        _isBeaconActive.value = active
    }

    fun clear() {
        cachedMessage = null
        _isBeaconActive.value = false
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_MESSAGE_ID = "key_cached_message_id"
        private const val KEY_CONTENT = "key_cached_content"
        private const val KEY_IS_URL = "key_cached_is_url"
        private const val KEY_TIMESTAMP = "key_cached_timestamp"

        @Volatile
        private var INSTANCE: LatestMessageRepository? = null

        fun getInstance(context: Context): LatestMessageRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = LatestMessageRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
