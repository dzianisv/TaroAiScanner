package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TarotReading(
    val cardName: String,
    val orientation: String, // "Upright" or "Reversed"
    val summary: String,
    val generalMeaning: String,
    val advice: String,
    val warning: String,
    val luckyElements: List<String>
)
