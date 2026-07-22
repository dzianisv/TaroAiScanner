package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TarotDao {

    @Query("SELECT * FROM tarot_readings WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllReadingsForUser(userId: String): Flow<List<TarotReadingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: TarotReadingEntity)

    @Query("DELETE FROM tarot_readings WHERE id = :id")
    suspend fun deleteReading(id: Long)

    @Query("DELETE FROM tarot_readings WHERE userId = :userId")
    suspend fun clearHistoryForUser(userId: String)

    // Chat history queries
    @Query("SELECT * FROM tarot_chat_messages WHERE userId = :userId ORDER BY timestamp ASC")
    fun getAllChatMessagesForUser(userId: String): Flow<List<TarotChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: TarotChatMessageEntity)

    @Query("DELETE FROM tarot_chat_messages WHERE userId = :userId")
    suspend fun clearChatHistoryForUser(userId: String)

    @Query("UPDATE tarot_readings SET userId = :newUserId WHERE userId = 'guest'")
    suspend fun mergeGuestReadings(newUserId: String)

    @Query("UPDATE tarot_chat_messages SET userId = :newUserId WHERE userId = 'guest'")
    suspend fun mergeGuestChatMessages(newUserId: String)

    // Settings Singleton queries
    @Query("SELECT * FROM tarot_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<TarotSettingsEntity?>

    @Query("SELECT * FROM tarot_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): TarotSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: TarotSettingsEntity)
}
