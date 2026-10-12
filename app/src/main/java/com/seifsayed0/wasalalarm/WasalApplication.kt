package com.seifsayed0.wasalalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.maplibre.android.MapLibre

class WasalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(TRACKING_CHANNEL, "تتبّع الرحلة", NotificationManager.IMPORTANCE_LOW).apply {
                description = "إشعار مستمر أثناء تتبّع الرحلة"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL, "تنبيه الوصول", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيه صوتي واهتزاز عند الاقتراب من الوجهة"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    companion object {
        const val TRACKING_CHANNEL = "wasal_tracking_v1"
        const val ALARM_CHANNEL = "wasal_alarm_v1"
    }
}
