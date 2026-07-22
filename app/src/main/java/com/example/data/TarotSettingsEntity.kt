package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tarot_settings")
data class TarotSettingsEntity(
    @PrimaryKey val id: Int = 1, // Singleton row
    val proxyUrl: String = "https://geminiproxy-us-central1-ais-us-east5-652628ab15984c6da.cloudfunctions.net",
    val signedInEmail: String = "",
    val signedInName: String = "",
    val signedInPhotoUrl: String = "",
    val isSignedIn: Boolean = false,
    val isGuest: Boolean = false,
    val offlineMode: Boolean = false,
    val customApiKey: String = "",
    val idToken: String = ""
)
