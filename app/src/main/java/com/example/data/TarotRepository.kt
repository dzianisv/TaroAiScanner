package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TarotRepository(private val tarotDao: TarotDao) {

    val allReadings: Flow<List<TarotReadingEntity>> = tarotDao.getAllReadings()
    
    val settingsFlow: Flow<TarotSettingsEntity> = tarotDao.getSettingsFlow().map { 
        it ?: TarotSettingsEntity() 
    }

    suspend fun saveReading(reading: TarotReadingEntity) {
        tarotDao.insertReading(reading)
    }

    suspend fun deleteReading(id: Long) {
        tarotDao.deleteReading(id)
    }

    suspend fun clearHistory() {
        tarotDao.clearHistory()
    }

    suspend fun getSettingsDirect(): TarotSettingsEntity {
        return tarotDao.getSettingsDirect() ?: TarotSettingsEntity()
    }

    suspend fun saveSettings(settings: TarotSettingsEntity) {
        tarotDao.saveSettings(settings)
    }

    suspend fun updateProxyUrl(url: String) {
        val current = getSettingsDirect()
        tarotDao.saveSettings(current.copy(proxyUrl = url))
    }

    suspend fun updateUserProfile(email: String, name: String, photoUrl: String, isSignedIn: Boolean) {
        val current = getSettingsDirect()
        tarotDao.saveSettings(
            current.copy(
                signedInEmail = email,
                signedInName = name,
                signedInPhotoUrl = photoUrl,
                isSignedIn = isSignedIn
            )
        )
    }
}
