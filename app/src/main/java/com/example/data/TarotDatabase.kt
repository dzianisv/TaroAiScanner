package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TarotReadingEntity::class, TarotSettingsEntity::class, TarotChatMessageEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(ListTypeConverter::class)
abstract class TarotDatabase : RoomDatabase() {
    abstract fun tarotDao(): TarotDao

    companion object {
        @Volatile
        private var INSTANCE: TarotDatabase? = null

        fun getDatabase(context: Context): TarotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TarotDatabase::class.java,
                    "tarot_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
