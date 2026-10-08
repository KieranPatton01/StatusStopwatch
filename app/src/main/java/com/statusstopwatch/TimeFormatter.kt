package com.statusstopwatch

import java.util.Locale

/**
 * Utility class providing progressive display formatting for stopwatch durations.
 *
 * Rules:
 * - Under 24 hours: Display hours and minutes (Xh Ym, e.g., "8h 20m" or "0h 14m").
 * - 1 to 30 days: Display days and hours (Xd Yh, e.g., "4d 11h").
 * - 30 days and above: Display months and days (Xmo Yd, e.g., "2mo 14d", assuming 30 days per month).
 */
object TimeFormatter {

    private const val MILLIS_PER_SECOND = 1000L
    private const val MILLIS_PER_MINUTE = 60L * MILLIS_PER_SECOND
    private const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
    private const val MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR
    private const val DAYS_PER_MONTH = 30L
    private const val MILLIS_PER_MONTH = DAYS_PER_MONTH * MILLIS_PER_DAY

    /**
     * Formats elapsed milliseconds into the progressive status bar string.
     *
     * @param elapsedMillis Total duration elapsed in milliseconds.
     * @return Formatted string according to the active duration tier.
     */
    fun format(elapsedMillis: Long): String {
        val nonNegativeMillis = if (elapsedMillis < 0L) 0L else elapsedMillis
        val totalDays = nonNegativeMillis / MILLIS_PER_DAY

        return when {
            totalDays < 1L -> {
                // Tier 1: Under 24 hours -> "Xh Ym"
                val hours = nonNegativeMillis / MILLIS_PER_HOUR
                val minutes = (nonNegativeMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
                "${hours}h ${minutes}m"
            }
            totalDays < DAYS_PER_MONTH -> {
                // Tier 2: 1 to 30 days -> "Xd Yh"
                val days = totalDays
                val hours = (nonNegativeMillis % MILLIS_PER_DAY) / MILLIS_PER_HOUR
                "${days}d ${hours}h"
            }
            else -> {
                // Tier 3: 30 days and above -> "Xmo Yd" (30 days per month)
                val months = totalDays / DAYS_PER_MONTH
                val remainingDays = totalDays % DAYS_PER_MONTH
                "${months}mo ${remainingDays}d"
            }
        }
    }

    /**
     * High-precision detailed formatting for the main activity UI.
     *
     * Displays:
     * - Under 24h: "HH:mm:ss" (e.g. "08:20:15")
     * - 24h and above: "Xd HH:mm:ss" (e.g. "4d 11:05:42")
     *
     * @param elapsedMillis Total duration elapsed in milliseconds.
     * @return High-precision formatted string.
     */
    fun formatDetailed(elapsedMillis: Long): String {
        val nonNegativeMillis = if (elapsedMillis < 0L) 0L else elapsedMillis
        val seconds = (nonNegativeMillis / MILLIS_PER_SECOND) % 60L
        val minutes = (nonNegativeMillis / MILLIS_PER_MINUTE) % 60L
        val hours = (nonNegativeMillis / MILLIS_PER_HOUR) % 24L
        val days = nonNegativeMillis / MILLIS_PER_DAY

        return if (days > 0L) {
            String.format(Locale.US, "%dd %02d:%02d:%02d", days, hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
}
