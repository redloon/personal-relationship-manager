package com.idontremember.personalcrm.ui.components

import com.idontremember.personalcrm.data.settings.DateDisplay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * JVM unit tests for the date-display preference rendering ([formatEventDate] /
 * [relativeDate]) — the P1 "absolute vs. relative" preference consumed by the
 * events list, event detail, and contact detail event history.
 */
class DateFormattingTest {

    // Fixed "now": local noon keeps 24h arithmetic away from midnight/DST edges.
    private val now: Long = Calendar.getInstance().apply {
        set(2024, Calendar.JUNE, 15, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun daysAgo(days: Long): Long = now - days * 24 * 60 * 60 * 1000L

    private fun daysAhead(days: Long): Long = now + days * 24 * 60 * 60 * 1000L

    @Test
    fun absoluteUsesTheRequestedPattern() {
        val expected = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            .format(Date(now))
        assertEquals(expected, formatEventDate(now, DateDisplay.ABSOLUTE, now))
    }

    @Test
    fun relativeTodayYesterdayTomorrow() {
        assertEquals("Today", relativeDate(daysAgo(0), now))
        assertEquals("Yesterday", relativeDate(daysAgo(1), now))
        assertEquals("Tomorrow", relativeDate(daysAhead(1), now))
    }

    @Test
    fun relativeDaysAgoAndInDays() {
        assertEquals("3 days ago", relativeDate(daysAgo(3), now))
        assertEquals("6 days ago", relativeDate(daysAgo(6), now))
        assertEquals("In 4 days", relativeDate(daysAhead(4), now))
    }

    @Test
    fun relativeWeeks() {
        assertEquals("1 week ago", relativeDate(daysAgo(7), now))
        assertEquals("3 weeks ago", relativeDate(daysAgo(21), now))
        assertEquals("In 2 weeks", relativeDate(daysAhead(14), now))
    }

    @Test
    fun relativeMonthsAndYears() {
        assertEquals("1 month ago", relativeDate(daysAgo(31), now))
        assertEquals("2 months ago", relativeDate(daysAgo(61), now))
        assertEquals("In 2 months", relativeDate(daysAhead(61), now))
        assertEquals("1 year ago", relativeDate(daysAgo(400), now))
        assertEquals("In 1 year", relativeDate(daysAhead(400), now))
    }

    @Test
    fun relativeIsWiredIntoFormatEventDate() {
        assertEquals("Yesterday", formatEventDate(daysAgo(1), DateDisplay.RELATIVE, now))
    }
}
