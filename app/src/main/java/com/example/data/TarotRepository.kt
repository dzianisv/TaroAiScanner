package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TarotRepository(private val tarotDao: TarotDao) {

    fun getReadingsForUser(userId: String): Flow<List<TarotReadingEntity>> =
        tarotDao.getAllReadingsForUser(userId)
    
    fun getChatMessagesForUser(userId: String): Flow<List<TarotChatMessageEntity>> =
        tarotDao.getAllChatMessagesForUser(userId)
    
    val settingsFlow: Flow<TarotSettingsEntity> = tarotDao.getSettingsFlow().map { 
        it ?: TarotSettingsEntity() 
    }

    suspend fun saveReading(reading: TarotReadingEntity) {
        tarotDao.insertReading(reading)
    }

    suspend fun deleteReading(id: Long) {
        tarotDao.deleteReading(id)
    }

    suspend fun clearHistoryForUser(userId: String) {
        tarotDao.clearHistoryForUser(userId)
    }

    suspend fun saveChatMessage(message: TarotChatMessageEntity) {
        tarotDao.insertChatMessage(message)
    }

    suspend fun clearChatHistoryForUser(userId: String) {
        tarotDao.clearChatHistoryForUser(userId)
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

    suspend fun updateOfflineMode(offline: Boolean) {
        val current = getSettingsDirect()
        tarotDao.saveSettings(current.copy(offlineMode = offline))
    }

    suspend fun updateCustomApiKey(key: String) {
        val current = getSettingsDirect()
        tarotDao.saveSettings(current.copy(customApiKey = key))
    }

    suspend fun updateUserProfile(email: String, name: String, photoUrl: String, isSignedIn: Boolean, isGuest: Boolean = false, idToken: String = "") {
        val current = getSettingsDirect()
        tarotDao.saveSettings(
            current.copy(
                signedInEmail = email,
                signedInName = name,
                signedInPhotoUrl = photoUrl,
                isSignedIn = isSignedIn,
                isGuest = isGuest,
                idToken = idToken
            )
        )
    }

    suspend fun updateIdToken(token: String) {
        val current = getSettingsDirect()
        tarotDao.saveSettings(current.copy(idToken = token))
    }

    suspend fun mergeGuestHistory(newUserId: String) {
        tarotDao.mergeGuestReadings(newUserId)
        tarotDao.mergeGuestChatMessages(newUserId)
    }
}
