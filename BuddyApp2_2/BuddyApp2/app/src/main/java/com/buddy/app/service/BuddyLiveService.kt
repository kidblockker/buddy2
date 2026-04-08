package com.buddy.app.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.buddy.app.BuddyApplication
import com.buddy.app.R
import com.buddy.app.ai.BuddyAI
import com.buddy.app.memory.MemoryRepository
import kotlinx.coroutines.*
import java.util.Calendar

class BuddyLiveService : Service() {

    companion object {
        // Broadcast to MainActivity to display + speak a proactive message
        const val ACTION_PROACTIVE = "com.buddy.app.PROACTIVE"
        const val EXTRA_MSG        = "msg"
        const val EXTRA_CATEGORY   = "category"
        var isRunning = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var memory: MemoryRepository
    private lateinit var ai: BuddyAI
    private var updateJob: Job? = null

    // Category rotation
    private val categories = listOf("news","science","tech","productivity","health","wisdom","warning","finance")
    private var catIndex = 0

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        memory = (application as BuddyApplication).memory
        val key = getSharedPreferences("buddy_prefs", MODE_PRIVATE).getString("api_key","") ?: ""
        ai = BuddyAI(memory, key)
        startForeground(102, buildNotif())
        scheduleUpdates()
    }

    private fun buildNotif() = NotificationCompat.Builder(this, BuddyApplication.CH_LIVE)
        .setSmallIcon(R.drawable.ic_buddy)
        .setContentTitle("Buddy is active")
        .setContentText("Keeping you updated")
        .setOngoing(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_MIN).build()

    private fun scheduleUpdates() {
        updateJob = scope.launch {
            // First update after 60 seconds
            delay(60_000)
            while (isActive) {
                try {
                    val apiKey = getSharedPreferences("buddy_prefs", MODE_PRIVATE).getString("api_key","") ?: ""
                    if (apiKey.isNotBlank()) {
                        ai.updateKey(apiKey)

                        val category = getSmartCategory()
                        val msg = ai.generateProactiveUpdate(category)

                        if (msg.isNotBlank()) {
                            broadcastProactive(msg, category)
                        }
                    }
                } catch (e: Exception) { /* silent */ }

                // Update every 8-15 minutes (varies to feel natural)
                val delayMins = (8..15).random()
                delay(delayMins * 60 * 1000L)
            }
        }
    }

    /** Pick category smartly based on time of day */
    private fun getSmartCategory(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 6..8   -> listOf("productivity","health","wisdom").random()
            hour in 9..12  -> listOf("news","tech","finance").random()
            hour in 13..17 -> listOf("science","tech","news").random()
            hour in 18..21 -> listOf("wisdom","health","warning").random()
            hour >= 22     -> listOf("wisdom","health").random()
            else           -> categories[catIndex++ % categories.size]
        }
    }

    private fun broadcastProactive(msg: String, category: String) {
        val intent = Intent(ACTION_PROACTIVE).apply {
            setPackage(packageName)
            putExtra(EXTRA_MSG, msg)
            putExtra(EXTRA_CATEGORY, category)
        }
        sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        isRunning = false
        updateJob?.cancel(); scope.cancel()
        super.onDestroy()
    }
}
