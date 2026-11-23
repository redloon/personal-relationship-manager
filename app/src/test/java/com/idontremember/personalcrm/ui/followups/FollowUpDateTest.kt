package com.idontremember.personalcrm.ui.followups

import java.util.Locale
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FollowUpDateTest {
    @Test fun plannedDateStaysOnItsCalendarDayAcrossTimezones() {
        val previous = TimeZone.getDefault()
        try {
            for (zone in listOf("America/Los_Angeles", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals("Nov 10, 2026", formatFollowUpDate("2026-11-10", Locale.US))
            }
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test fun plannedDateUsesTheReadersLocale() {
        assertEquals("10 Nov 2026", formatFollowUpDate("2026-11-10", Locale.UK))
    }
}
