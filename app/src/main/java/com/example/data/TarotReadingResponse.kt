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
    val luckyElements: List<String>,
    /**
     * True when this reading did NOT come from the AI -- it is the bundled
     * offline/sample interpretation. The UI MUST label such a reading so it is
     * never mistaken for a real Gemini answer. Defaulted so responses parsed
     * from the model (which never carry this field) are always `false`.
     */
    val isOffline: Boolean = false
)
