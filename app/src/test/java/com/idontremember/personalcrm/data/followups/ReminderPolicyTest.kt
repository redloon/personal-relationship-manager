package com.idontremember.personalcrm.data.followups

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderPolicyTest {
    @Test fun noReminderBeforeMorningOrAfterEvening() {
        assertFalse(ReminderPolicy.canDeliver(LocalDateTime.of(2026, 11, 1, 8, 59), 9, null))
        assertTrue(ReminderPolicy.canDeliver(LocalDateTime.of(2026, 11, 1, 9, 0), 9, null))
        assertFalse(ReminderPolicy.canDeliver(LocalDateTime.of(2026, 11, 1, 20, 0), 9, null))
    }
    @Test fun atMostOneSummaryPerLocalDay() {
        assertFalse(ReminderPolicy.canDeliver(LocalDateTime.of(2026, 11, 1, 12, 0), 9, "2026-11-01"))
        assertTrue(ReminderPolicy.canDeliver(LocalDateTime.of(2026, 11, 2, 12, 0), 9, "2026-11-01"))
    }
    @Test fun onlyExplicitDueDatesAreEligible() {
        val today = LocalDate.of(2026, 11, 1)
        assertFalse(ReminderPolicy.isDue(null, today))
        assertFalse(ReminderPolicy.isDue("2026-11-02", today))
        assertTrue(ReminderPolicy.isDue("2026-11-01", today))
        assertTrue(ReminderPolicy.isDue("2026-10-01", today))
        assertFalse(ReminderPolicy.isDue("invalid", today))
    }
}
