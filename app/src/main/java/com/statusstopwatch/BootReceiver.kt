package com.statusstopwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * BroadcastReceiver triggered on system reboot and fast-boot events.
 *
 * Restores the foreground service automatically if the stopwatch was left running
 * prior to device shutdown or restart.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val preferences = StopwatchPreferences(context)
            if (preferences.isRunning) {
                val serviceIntent = Intent(context, StopwatchService::class.java).apply {
                    this.action = StopwatchService.ACTION_START
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
