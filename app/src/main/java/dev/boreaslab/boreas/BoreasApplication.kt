package dev.boreaslab.boreas

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dev.boreaslab.boreas.service.SessionNotifications

class BoreasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                SessionNotifications.CHANNEL_ID,
                getString(R.string.notification_channel_session),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_session_detail)
                setShowBadge(false)
            },
        )
    }
}
