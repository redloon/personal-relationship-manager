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
 *
 * P2 moved all wording to strings.xml: [RelativeDateStrings] is injected here
 * with English doubles matching `res/values/strings.xml`, keeping the date math
 * pure and JVM-testable.
 */
class DateFormattingTest {

    // Fixed "now": local noon keeps 24h arithmetic away from midnight/DST edges.
    private val now: Long = Calendar.getInstance().apply {
        set(2024, Calendar.JUNE, 15, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // English copy mirroring res/values/strings.xml.
    private val strings = RelativeDateStrings(
        today = "Today",
        yesterday = "Yesterday",
        tomorrow = "Tomorrow",
        daysAgo = { "$it days ago" },
        inDays = { "In $it days" },
        weeksAgo = { if (it == 1) "1 week ago" else "$it weeks ago" },
        inWeeks = { if (it == 1) "In 1 week" else "In $it weeks" },
        monthsAgo = { if (it == 1) "1 month ago" else "$it months ago" },
        inMonths = { if (it == 1) "In 1 month" else "In $it months" },
        yearsAgo = { if (it == 1) "1 year ago" else "$it years ago" },
        inYears = { if (it == 1) "In 1 year" else "In $it years" }
    )

    private val pattern = "MMM d, yyyy 'at' h:mm a"

    private fun daysAgo(days: Long): Long = now - days * 24 * 60 * 60 * 1000L

    private fun daysAhead(days: Long): Long = now + days * 24 * 60 * 60 * 1000L

    @Test
    fun absoluteUsesTheRequestedPattern() {
        val expected = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
        assertEquals(expected, formatEventDate(now, DateDisplay.ABSOLUTE, strings, pattern, now))
    }

    @Test
    fun relativeTodayYesterdayTomorrow() {
        assertEquals("Today", relativeDate(daysAgo(0), strings, now))
        assertEquals("Yesterday", relativeDate(daysAgo(1), strings, now))
        assertEquals("Tomorrow", relativeDate(daysAhead(1), strings, now))
    }

    @Test
    fun relativeDaysAgoAndInDays() {
        assertEquals("3 days ago", relativeDate(daysAgo(3), strings, now))
        assertEquals("6 days ago", relativeDate(daysAgo(6), strings, now))
        assertEquals("In 4 days", relativeDate(daysAhead(4), strings, now))
    }

    @Test
    fun relativeWeeks() {
        assertEquals("1 week ago", relativeDate(daysAgo(7), strings, now))
        assertEquals("3 weeks ago", relativeDate(daysAgo(21), strings, now))
        assertEquals("In 2 weeks", relativeDate(daysAhead(14), strings, now))
    }

    @Test
    fun relativeMonthsAndYears() {
        assertEquals("1 month ago", relativeDate(daysAgo(31), strings, now))
        assertEquals("2 months ago", relativeDate(daysAgo(61), strings, now))
        assertEquals("In 2 months", relativeDate(daysAhead(61), strings, now))
        assertEquals("1 year ago", relativeDate(daysAgo(400), strings, now))
        assertEquals("In 1 year", relativeDate(daysAhead(400), strings, now))
    }

    @Test
    fun relativeIsWiredIntoFormatEventDate() {
        assertEquals(
            "Yesterday",
            formatEventDate(daysAgo(1), DateDisplay.RELATIVE, strings, pattern, now)
        )
    }
}
