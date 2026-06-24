package com.remotecontrol.server

import android.content.Context
import android.os.PowerManager

object DeviceWakeController {
    private const val WAKE_DURATION_MS = 30_000L

    @Suppress("DEPRECATION")
    fun wakeScreen(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isInteractive) return

        powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "${context.packageName}:RemoteWake"
        ).acquire(WAKE_DURATION_MS)
    }
}
