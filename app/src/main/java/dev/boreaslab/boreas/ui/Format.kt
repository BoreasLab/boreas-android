package dev.boreaslab.boreas.ui

import java.util.Locale

/** Locale-aware display formatting with binary byte units and stable widths. */

fun formatCount(value: Long): String = String.format(Locale.getDefault(), "%,d", value)

/** Elapsed time as h:mm:ss, or m:ss below an hour, with fixed-width fields. */
fun formatDuration(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

fun formatClockTime(millis: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))
