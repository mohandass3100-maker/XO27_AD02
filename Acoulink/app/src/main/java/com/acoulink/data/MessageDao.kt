package com.acoulink.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for persisting and querying acoustic messages.
 */
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessagesDescending(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessagesAscending(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE direction = :direction ORDER BY timestamp DESC")
    fun getMessagesByDirection(direction: MessageDirection): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE recoveryStatus = 'RECOVERED' ORDER BY timestamp DESC")
    fun getRecoveredMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE transmissionStatus = 'FAILED' OR crcStatus = 'CORRUPTED' ORDER BY timestamp DESC")
    fun getFailedMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' OR messageId LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    fun getMessageById(id: Long): Flow<MessageEntity?>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
