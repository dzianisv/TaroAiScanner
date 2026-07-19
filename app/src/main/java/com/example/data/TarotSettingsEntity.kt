package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tarot_settings")
data class TarotSettingsEntity(
    @PrimaryKey val id: Int = 1, // Singleton row
    val proxyUrl: String = "",
    val signedInEmail: String = "",
    val signedInName: String = "",
    val signedInPhotoUrl: String = "",
    val isSignedIn: Boolean = false
)
