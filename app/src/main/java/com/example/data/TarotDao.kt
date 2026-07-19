package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TarotDao {

    @Query("SELECT * FROM tarot_readings ORDER BY timestamp DESC")
    fun getAllReadings(): Flow<List<TarotReadingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: TarotReadingEntity)

    @Query("DELETE FROM tarot_readings WHERE id = :id")
    suspend fun deleteReading(id: Long)

    @Query("DELETE FROM tarot_readings")
    suspend fun clearHistory()

    // Settings Singleton queries
    @Query("SELECT * FROM tarot_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<TarotSettingsEntity?>

    @Query("SELECT * FROM tarot_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): TarotSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: TarotSettingsEntity)
}
