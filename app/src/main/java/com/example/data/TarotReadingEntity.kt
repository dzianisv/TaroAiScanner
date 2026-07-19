package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@Entity(tableName = "tarot_readings")
data class TarotReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardName: String,
    val orientation: String,
    val summary: String,
    val generalMeaning: String,
    val advice: String,
    val warning: String,
    val luckyElementsJson: String, // Stored as JSON string
    val spreadType: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ListTypeConverter {
    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = moshi.adapter<List<String>>(listType)

    @TypeConverter
    fun fromString(value: String): List<String>? {
        return try {
            adapter.fromJson(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        return adapter.toJson(list ?: emptyList())
    }
}
