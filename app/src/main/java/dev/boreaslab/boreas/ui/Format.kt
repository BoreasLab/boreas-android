package dev.boreaslab.boreas.ui

import java.util.Locale
import kotlin.math.abs

/**
 * Number and duration formatting for display.
 *
 * Kept as pure functions so a counter never formats itself differently in two
 * places, and so the formatting can be read without running the app. Grouping uses
 * the reader's locale; the units below are the binary ones the network stack
 * actually reports.
 */

/** 1 234 567 becomes "1,234,567" in an English locale. */
fun formatCount(value: Long): String = String.format(Locale.getDefault(), "%,d", value)

/** Bytes at three significant figures, so the column width stays stable. */
fun formatBytes(value: Long): String {
    val negative = value < 0
    var amount = abs(value).toDouble()
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var unit = 0
    while (amount >= 1024 && unit < units.lastIndex) {
        amount /= 1024
        unit += 1
    }
    val text = when {
        unit == 0 -> String.format(Locale.getDefault(), "%.0f %s", amount, units[unit])
        amount >= 100 -> String.format(Locale.getDefault(), "%.0f %s", amount, units[unit])
        amount >= 10 -> String.format(Locale.getDefault(), "%.1f %s", amount, units[unit])
        else -> String.format(Locale.getDefault(), "%.2f %s", amount, units[unit])
    }
    return if (negative) "-$text" else text
}

/**
 * Elapsed time as h:mm:ss, or m:ss below an hour.
 *
 * Fixed-width fields so the digits do not reflow every second while the reader is
 * looking at them.
 */
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

/** Clock time for a recorded event, in the reader's 24 hour or 12 hour preference. */
fun formatClockTime(millis: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))
