package com.redloon.prm.data.followups

import java.time.LocalDate
import java.time.LocalDateTime

object ReminderPolicy {
    fun canDeliver(now: LocalDateTime, hour: Int, lastDay: String?): Boolean =
        now.hour >= hour && now.hour < 20 && lastDay != now.toLocalDate().toString()

    fun isDue(date: String?, today: LocalDate): Boolean =
        date != null && runCatching { !LocalDate.parse(date).isAfter(today) }.getOrDefault(false)
}
