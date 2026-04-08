package com.buddy.app.memory
import androidx.room.*

@Dao interface InteractionDao {
    @Insert suspend fun insert(i: InteractionEntity): Long
    @Query("SELECT * FROM interactions ORDER BY timestamp DESC LIMIT :n") suspend fun getLastN(n: Int): List<InteractionEntity>
    @Query("SELECT COUNT(*) FROM interactions") suspend fun count(): Int
    @Query("DELETE FROM interactions WHERE id IN (SELECT id FROM interactions ORDER BY timestamp ASC LIMIT :n)") suspend fun deleteOldest(n: Int)
}

@Dao interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun set(p: UserProfileEntity)
    @Query("SELECT * FROM user_profile WHERE `key` = :key") suspend fun get(key: String): UserProfileEntity?
    @Query("SELECT * FROM user_profile") suspend fun getAll(): List<UserProfileEntity>
}
