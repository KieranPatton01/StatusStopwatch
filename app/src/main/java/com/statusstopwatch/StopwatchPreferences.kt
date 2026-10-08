package com.statusstopwatch

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages stopwatch persistence across process lifecycles, app kills, and system reboots.
 *
 * The stopwatch does NOT rely on in-memory counters or periodic tick accumulation.
 * Instead, it anchors start events to wall-clock epoch timestamps (System.currentTimeMillis()),
 * enabling zero-drift duration calculation across arbitrary timeframes and device power cycles.
 */
class StopwatchPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "status_stopwatch_preferences"

        const val KEY_START_TIME = "key_start_time"
        const val KEY_ACCUMULATED_TIME = "key_accumulated_time"
        const val KEY_IS_RUNNING = "key_is_running"

        const val KEY_OFFSET_X = "key_offset_x"
        const val KEY_OFFSET_Y = "key_offset_y"
        const val KEY_OVERLAY_ENABLED = "key_overlay_enabled"

        // Default position calibrated to sit in status bar next to typical camera cutouts
        const val DEFAULT_OFFSET_X = 80 // dp
        const val DEFAULT_OFFSET_Y = 2  // dp
    }

    /**
     * Epoch millisecond timestamp when the stopwatch was started or resumed.
     * Set to 0 when the stopwatch is paused or reset.
     */
    var startTime: Long
        get() = prefs.getLong(KEY_START_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_START_TIME, value).apply()

    /**
     * Accumulated elapsed duration in milliseconds prior to the current running session.
     */
    var accumulatedTime: Long
        get() = prefs.getLong(KEY_ACCUMULATED_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_ACCUMULATED_TIME, value).apply()

    /**
     * True if the stopwatch is currently active and running.
     */
    var isRunning: Boolean
        get() = prefs.getBoolean(KEY_IS_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_RUNNING, value).apply()

    /**
     * Horizontal X offset (in dp) for overlay positioning in the status bar.
     */
    var offsetX: Int
        get() = prefs.getInt(KEY_OFFSET_X, DEFAULT_OFFSET_X)
        set(value) = prefs.edit().putInt(KEY_OFFSET_X, value).apply()

    /**
     * Vertical Y offset (in dp) for overlay positioning in the status bar.
     */
    var offsetY: Int
        get() = prefs.getInt(KEY_OFFSET_Y, DEFAULT_OFFSET_Y)
        set(value) = prefs.edit().putInt(KEY_OFFSET_Y, value).apply()

    /**
     * Controls whether the status bar overlay pill is drawn.
     */
    var isOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    /**
     * Computes the dynamic elapsed duration using epoch time:
     * System.currentTimeMillis() - startTime, plus any accumulated time from prior sessions.
     *
     * @return Total elapsed milliseconds.
     */
    fun getElapsedTime(): Long {
        val currentStart = startTime
        val accumulated = accumulatedTime

        return if (isRunning) {
            val now = System.currentTimeMillis()
            if (currentStart > 0L && now >= currentStart) {
                accumulated + (now - currentStart)
            } else {
                accumulated
            }
        } else {
            accumulated
        }
    }

    /**
     * Starts or resumes the stopwatch.
     * Anchors the start epoch timestamp and marks the stopwatch as running.
     */
    fun start() {
        if (!isRunning) {
            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong(KEY_START_TIME, now)
                .putBoolean(KEY_IS_RUNNING, true)
                .apply()
        }
    }

    /**
     * Pauses the stopwatch.
     * Persists the exact elapsed duration into accumulatedTime and clears the running state.
     */
    fun pause() {
        if (isRunning) {
            val totalElapsed = getElapsedTime()
            prefs.edit()
                .putLong(KEY_START_TIME, 0L)
                .putLong(KEY_ACCUMULATED_TIME, totalElapsed)
                .putBoolean(KEY_IS_RUNNING, false)
                .apply()
        }
    }

    /**
     * Resets the stopwatch to 0.
     */
    fun reset() {
        prefs.edit()
            .putLong(KEY_START_TIME, 0L)
            .putLong(KEY_ACCUMULATED_TIME, 0L)
            .putBoolean(KEY_IS_RUNNING, false)
            .apply()
    }
}
