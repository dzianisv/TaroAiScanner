package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tarot_chat_messages")
data class TarotChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = "guest",
    val sender: String, // "user" or "model"
    val text: String,
    val mediaUri: String? = null, // URI of attached image or video
    val mediaType: String? = null, // "image" or "video"
    val timestamp: Long = System.currentTimeMillis()
)
