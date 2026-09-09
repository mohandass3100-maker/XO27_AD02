package com.acoulink.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository orchestrating local message persistence with strict privacy consent enforcement.
 */
class MessageRepository(
    private val messageDao: MessageDao,
    context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("acoulink_privacy_prefs", Context.MODE_PRIVATE)

    private val _isHistoryEnabled = MutableStateFlow(prefs.getBoolean(KEY_HISTORY_ENABLED, true))
    val isHistoryEnabled = _isHistoryEnabled.asStateFlow()

    private val _askBeforeSaving = MutableStateFlow(prefs.getBoolean(KEY_ASK_BEFORE_SAVING, true))
    val askBeforeSaving = _askBeforeSaving.asStateFlow()

    fun setHistoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HISTORY_ENABLED, enabled).apply()
        _isHistoryEnabled.value = enabled
    }

    fun setAskBeforeSaving(ask: Boolean) {
        prefs.edit().putBoolean(KEY_ASK_BEFORE_SAVING, ask).apply()
        _askBeforeSaving.value = ask
    }

    /**
     * Persists a message only if history storage is enabled and caller confirms user consent.
     * Returns true if saved, false otherwise.
     */
    suspend fun saveMessageWithConsent(message: MessageEntity): Boolean = withContext(Dispatchers.IO) {
        if (!_isHistoryEnabled.value) {
            return@withContext false
        }
        messageDao.insert(message)
        return@withContext true
    }

    fun getAllMessages(sortAscending: Boolean = false): Flow<List<MessageEntity>> {
        return if (sortAscending) {
            messageDao.getAllMessagesAscending()
        } else {
            messageDao.getAllMessagesDescending()
        }
    }

    fun getMessagesByDirection(direction: MessageDirection): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByDirection(direction)
    }

    fun getRecoveredMessages(): Flow<List<MessageEntity>> {
        return messageDao.getRecoveredMessages()
    }

    fun getFailedMessages(): Flow<List<MessageEntity>> {
        return messageDao.getFailedMessages()
    }

    fun searchMessages(query: String): Flow<List<MessageEntity>> {
        return messageDao.searchMessages(query)
    }

    fun getMessageById(id: Long): Flow<MessageEntity?> {
        return messageDao.getMessageById(id)
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        messageDao.deleteById(id)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        messageDao.deleteAll()
    }

    companion object {
        private const val KEY_HISTORY_ENABLED = "key_history_enabled"
        private const val KEY_ASK_BEFORE_SAVING = "key_ask_before_saving"

        @Volatile
        private var INSTANCE: MessageRepository? = null

        fun getInstance(context: Context): MessageRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                val instance = MessageRepository(db.messageDao(), context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
