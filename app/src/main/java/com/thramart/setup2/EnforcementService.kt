package com.thramart.setup2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class EnforcementService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val checker = object : Runnable {
        override fun run() {
            try {
                KioskPolicy.applyPolicies(this@EnforcementService)
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channelId = "kiosk_enforcement"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Kiosk protection", NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Thramart Kiosk active")
            .setContentText("App schedule and kiosk lock are being enforced")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
        startForeground(2002, notification)
        handler.post(checker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(checker)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
