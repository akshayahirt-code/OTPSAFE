package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE isSpam = 0 ORDER BY timestamp DESC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isVaultMessage = 0 AND isSpam = 0 ORDER BY timestamp DESC")
    fun getNormalMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isVaultMessage = 1 AND isSpam = 0 ORDER BY timestamp DESC")
    fun getVaultMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesInThreadFlow(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    suspend fun getMessagesInThread(threadId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    @Query("UPDATE messages SET isSpam = 1 WHERE threadId = :threadId")
    suspend fun markThreadAsSpam(threadId: String)

    @Query("DELETE FROM messages WHERE isOtp = 1 AND timestamp < :thresholdTime")
    suspend fun deleteOtpsOlderThan(thresholdTime: Long): Int

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun clearThread(threadId: String)

    // Config queries
    @Query("SELECT * FROM vault_config WHERE id = 1")
    fun getVaultConfigFlow(): Flow<VaultConfigEntity?>

    @Query("SELECT * FROM vault_config WHERE id = 1")
    suspend fun getVaultConfig(): VaultConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVaultConfig(config: VaultConfigEntity)
}
