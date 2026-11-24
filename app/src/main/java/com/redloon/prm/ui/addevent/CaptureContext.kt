package com.redloon.prm.ui.addevent

import java.util.Calendar
import java.util.TimeZone

/** New capture and edits must never overwrite one another's persisted work. */
fun captureDraftKey(eventId: Long? = null, factId: Long? = null): String = when {
    eventId != null -> "event_$eventId"
    factId != null -> "fact_$factId"
    else -> "draft"
}

/** Material date pickers use UTC date components; retain the note's local time. */
fun withPickedDate(original: Long, pickedUtc: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtc }
    return Calendar.getInstance(zone).apply {
        timeInMillis = original
        set(Calendar.YEAR, date.get(Calendar.YEAR))
        set(Calendar.MONTH, date.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
