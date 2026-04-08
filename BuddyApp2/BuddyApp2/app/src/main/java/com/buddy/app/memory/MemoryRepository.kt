package com.buddy.app.memory
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryRepository(context: Context) {
    private val db   = MemoryDatabase.get(context)
    private val iDao = db.interactionDao()
    private val pDao = db.userProfileDao()

    companion object {
        const val NAME = "name"; const val AGE = "age"
        const val OCCUPATION = "occupation"; const val CITY = "city"
        const val INTERESTS = "interests"; const val SESSIONS = "sessions"
    }

    suspend fun saveInteraction(user: String, buddy: String) = withContext(Dispatchers.IO) {
        iDao.insert(InteractionEntity(userMessage = user, buddyResponse = buddy))
        val c = iDao.count(); if (c > 300) iDao.deleteOldest(c - 300)
    }

    suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        pDao.set(UserProfileEntity(key, value))
    }
    suspend fun get(key: String): String? = withContext(Dispatchers.IO) { pDao.get(key)?.value }

    suspend fun incrementSessions() = withContext(Dispatchers.IO) {
        val n = (pDao.get(SESSIONS)?.value?.toIntOrNull() ?: 0) + 1
        pDao.set(UserProfileEntity(SESSIONS, n.toString()))
    }

    suspend fun contextString(): String = withContext(Dispatchers.IO) {
        val profile = pDao.getAll()
        val recent  = iDao.getLastN(6)
        val sb = StringBuilder()
        if (profile.isNotEmpty()) {
            sb.append("USER PROFILE:\n")
            profile.filter { it.key != SESSIONS }.forEach { sb.append("- ${it.key}: ${it.value}\n") }
        }
        if (recent.isNotEmpty()) {
            sb.append("\nRECENT CONVERSATION:\n")
            recent.reversed().forEach {
                sb.append("User: ${it.userMessage.take(80)}\n")
                sb.append("Buddy: ${it.buddyResponse.take(100)}\n")
            }
        }
        sb.toString().ifBlank { "New user, no history yet." }
    }
}
