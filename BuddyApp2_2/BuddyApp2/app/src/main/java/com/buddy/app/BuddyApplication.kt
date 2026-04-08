package com.buddy.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.buddy.app.memory.MemoryRepository

class BuddyApplication : Application() {
    companion object {
        const val CH_LIVE    = "buddy_live"
        const val CH_WAKE    = "buddy_wake"
        const val CH_ALERT   = "buddy_alert"
        lateinit var instance: BuddyApplication private set
    }
    lateinit var memory: MemoryRepository private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        memory = MemoryRepository(this)
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_LIVE, "Buddy Active", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
            nm.createNotificationChannel(NotificationChannel(CH_WAKE, "Buddy Listening", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
            nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Buddy Updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
