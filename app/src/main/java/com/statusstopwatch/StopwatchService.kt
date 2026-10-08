package com.statusstopwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Persistent Foreground Service maintaining status bar overlay pill and ongoing notification.
 *
 * Designed to satisfy Android 15 (targetSdk 35) requirements with foregroundServiceType="specialUse".
 * Ticks on the main Looper Handler to update both the status bar pill and notification text.
 */
class StopwatchService : Service() {

    companion object {
        const val ACTION_START = "com.statusstopwatch.action.START"
        const val ACTION_PAUSE = "com.statusstopwatch.action.PAUSE"
        const val ACTION_RESET = "com.statusstopwatch.action.RESET"
        const val ACTION_UPDATE_OFFSETS = "com.statusstopwatch.action.UPDATE_OFFSETS"
        const val ACTION_TOGGLE_OVERLAY = "com.statusstopwatch.action.TOGGLE_OVERLAY"
        const val ACTION_STOP = "com.statusstopwatch.action.STOP"

        const val NOTIFICATION_CHANNEL_ID = "status_stopwatch_channel"
        const val NOTIFICATION_ID = 1001

        const val BROADCAST_TIME_TICK = "com.statusstopwatch.broadcast.TIME_TICK"
        const val EXTRA_ELAPSED_MILLIS = "extra_elapsed_millis"
        const val EXTRA_IS_RUNNING = "extra_is_running"
    }

    private lateinit var preferences: StopwatchPreferences
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayAttached = false
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())
    private val tickerRunnable = object : Runnable {
        override fun run() {
            updateTick()
            if (preferences.isRunning) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = StopwatchPreferences(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                preferences.start()
                startForegroundWithNotification()
                ensureOverlay()
                startTicker()
            }
            ACTION_PAUSE -> {
                preferences.pause()
                stopTicker()
                updateTick()
            }
            ACTION_RESET -> {
                preferences.reset()
                stopTicker()
                updateTick()
            }
            ACTION_UPDATE_OFFSETS -> {
                updateOverlayPosition()
            }
            ACTION_TOGGLE_OVERLAY -> {
                if (preferences.isOverlayEnabled) {
                    ensureOverlay()
                } else {
                    removeOverlay()
                }
            }
            ACTION_STOP -> {
                preferences.pause()
                stopTicker()
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTicker()
        removeOverlay()
        super.onDestroy()
    }

    private fun startTicker() {
        handler.removeCallbacks(tickerRunnable)
        handler.post(tickerRunnable)
    }

    private fun stopTicker() {
        handler.removeCallbacks(tickerRunnable)
    }

    /**
     * Executes single tick update across notification, overlay pill, and broadcast receiver.
     */
    private fun updateTick() {
        val elapsedMillis = preferences.getElapsedTime()
        val formattedProgressive = TimeFormatter.format(elapsedMillis)

        // 1. Update Notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(formattedProgressive))

        // 2. Update Overlay Pill View
        updateOverlayText(formattedProgressive)

        // 3. Send Broadcast for Activity UI sync
        val broadcastIntent = Intent(BROADCAST_TIME_TICK).apply {
            setPackage(packageName)
            putExtra(EXTRA_ELAPSED_MILLIS, elapsedMillis)
            putExtra(EXTRA_IS_RUNNING, preferences.isRunning)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun startForegroundWithNotification() {
        val formatted = TimeFormatter.format(preferences.getElapsedTime())
        val notification = buildNotification(formatted)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(formattedTime: String): Notification {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (preferences.isRunning) {
            val pauseIntent = Intent(this, StopwatchService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getService(
                this,
                1,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                R.drawable.ic_pause,
                "Pause",
                pausePendingIntent
            ).build()
        } else {
            val startIntent = Intent(this, StopwatchService::class.java).apply {
                action = ACTION_START
            }
            val startPendingIntent = PendingIntent.getService(
                this,
                2,
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                R.drawable.ic_play,
                "Resume",
                startPendingIntent
            ).build()
        }

        val resetIntent = Intent(this, StopwatchService::class.java).apply {
            action = ACTION_RESET
        }
        val resetPendingIntent = PendingIntent.getService(
            this,
            3,
            resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val resetAction = NotificationCompat.Action.Builder(
            R.drawable.ic_reset,
            "Reset",
            resetPendingIntent
        ).build()

        val statusText = if (preferences.isRunning) "Running" else "Paused"

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stopwatch)
            .setContentTitle("Status Stopwatch ($statusText)")
            .setContentText("⏱ $formattedTime")
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(pauseResumeAction)
            .addAction(resetAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Status Stopwatch Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing persistent stopwatch duration display"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // ==========================================
    // STATUS BAR OVERLAY PILL IMPLEMENTATION
    // ==========================================

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun ensureOverlay() {
        if (!preferences.isOverlayEnabled || !canDrawOverlays()) {
            removeOverlay()
            return
        }

        if (overlayView == null) {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_pill, null)

            // Tapping the overlay pill launches MainActivity
            val clickListener = View.OnClickListener {
                val openIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(openIntent)
            }
            overlayView?.setOnClickListener(clickListener)
            overlayView?.findViewById<View>(R.id.pill_container)?.setOnClickListener(clickListener)

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            overlayLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dpToPx(preferences.offsetX)
                y = dpToPx(preferences.offsetY)
            }

            try {
                windowManager?.addView(overlayView, overlayLayoutParams)
                isOverlayAttached = true
            } catch (e: Exception) {
                e.printStackTrace()
                isOverlayAttached = false
            }
        }

        updateOverlayText(TimeFormatter.format(preferences.getElapsedTime()))
    }

    private fun updateOverlayPosition() {
        if (overlayView != null && isOverlayAttached && overlayLayoutParams != null) {
            overlayLayoutParams?.apply {
                x = dpToPx(preferences.offsetX)
                y = dpToPx(preferences.offsetY)
            }
            try {
                windowManager?.updateViewLayout(overlayView, overlayLayoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateOverlayText(formattedTime: String) {
        if (overlayView != null && isOverlayAttached) {
            val textView = overlayView?.findViewById<TextView>(R.id.tv_pill_text)
            textView?.text = formattedTime
        }
    }

    private fun removeOverlay() {
        if (overlayView != null && isOverlayAttached) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
            isOverlayAttached = false
            overlayLayoutParams = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
