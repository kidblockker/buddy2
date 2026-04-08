package com.buddy.app.memory
import androidx.room.*

@Entity(tableName = "interactions")
data class InteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userMessage: String, val buddyResponse: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
