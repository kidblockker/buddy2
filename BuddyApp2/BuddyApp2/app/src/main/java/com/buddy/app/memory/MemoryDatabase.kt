package com.buddy.app.memory
import android.content.Context
import androidx.room.*

@Database(entities = [InteractionEntity::class, UserProfileEntity::class], version = 1, exportSchema = false)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun interactionDao(): InteractionDao
    abstract fun userProfileDao(): UserProfileDao
    companion object {
        @Volatile private var INSTANCE: MemoryDatabase? = null
        fun get(context: Context) = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, MemoryDatabase::class.java, "buddy.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
