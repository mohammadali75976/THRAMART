package com.thramart.setup2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!KioskPolicy.isDeviceOwner(context)) return
        try {
            KioskPolicy.applyPolicies(context)
            val service = Intent(context, EnforcementService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        } catch (_: Exception) {
        }
    }
}
