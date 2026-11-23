package com.idontremember.personalcrm.ui.followups

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Planned dates are calendar dates, so timezone conversion must not move them. */
fun formatFollowUpDate(date: String, locale: Locale = Locale.getDefault()): String =
    LocalDate.parse(
        date
    ).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
