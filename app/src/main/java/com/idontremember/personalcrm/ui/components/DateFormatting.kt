package com.idontremember.personalcrm.ui.components

import com.idontremember.personalcrm.data.settings.DateDisplay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Renders an event timestamp according to the user's [DateDisplay] preference.
 * Used by the events list, event detail, and contact detail event history so the
 * P1 preference is honored everywhere event dates appear.
 */
fun formatEventDate(
    timestamp: Long,
    dateDisplay: DateDisplay,
    now: Long = System.currentTimeMillis(),
    absolutePattern: String = "MMM d, yyyy 'at' h:mm a"
): String = when (dateDisplay) {
    DateDisplay.ABSOLUTE -> SimpleDateFormat(absolutePattern, Locale.getDefault())
        .format(Date(timestamp))

    DateDisplay.RELATIVE -> relativeDate(timestamp, now)
}

/**
 * "Today" / "Yesterday" / "3 days ago" / "In 2 weeks" style label for [timestamp]
 * relative to [now]. Pure function ([now] injectable) so it is JVM-unit-testable.
 */
fun relativeDate(
    timestamp: Long,
    now: Long = System.currentTimeMillis()
): String {
    val days = localDayNumber(now) - localDayNumber(timestamp) // positive = in the past
    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days == -1L -> "Tomorrow"
        days in 2L..6L -> "$days days ago"
        days in -6L..-2L -> "In ${-days} days"
        days in 7L..29L -> "${counted(days / 7, "week")} ago"
        days in -29L..-7L -> "In ${counted(-days / 7, "week")}"
        days in 30L..364L -> "${counted(days / 30, "month")} ago"
        days in -364L..-30L -> "In ${counted(-days / 30, "month")}"
        days >= 365L -> "${counted(days / 365, "year")} ago"
        else -> "In ${counted(-days / 365, "year")}"
    }
}

private fun counted(count: Long, unit: String): String =
    if (count == 1L) "1 $unit" else "$count ${unit}s"

/**
 * Day ordinal of [timestamp] in the local timezone (midnight-truncated). Rounding
 * absorbs DST shifts so consecutive local midnights are exactly one day apart.
 */
private fun localDayNumber(timestamp: Long): Long {
    val midnight = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return Math.round(midnight.toDouble() / DAY_IN_MILLIS)
}
