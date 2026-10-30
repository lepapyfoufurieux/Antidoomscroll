package com.antiscrolling.notif

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class Notifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_QUOTA, "Alerte avant la limite", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun quotaSoon(packageName: String, label: String) {
        // Permission optionnelle (Android 13+) : sans elle, pas d'alerte, le blocage reste.
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = Notification.Builder(context, CHANNEL_QUOTA)
            // shortcut: icône système, à remplacer par celle de l'appli quand elle existera.
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$label : plus que 5 min")
            .setContentText("Le temps prévu pour aujourd'hui est presque écoulé.")
            .setAutoCancel(true)
            .build()
        manager.notify(packageName.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_QUOTA = "quota"
    }
}
