package com.idontremember.personalcrm.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.settings.DateDisplay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Localized copy used by [relativeDate]. Built from `strings.xml` via
 * [relativeDateStrings]; kept injectable so the date math stays pure and
 * JVM-unit-testable (P2: no hardcoded user-visible strings).
 */
data class RelativeDateStrings(
    val today: String,
    val yesterday: String,
    val tomorrow: String,
    val daysAgo: (Int) -> String,
    val inDays: (Int) -> String,
    val weeksAgo: (Int) -> String,
    val inWeeks: (Int) -> String,
    val monthsAgo: (Int) -> String,
    val inMonths: (Int) -> String,
    val yearsAgo: (Int) -> String,
    val inYears: (Int) -> String
)

/**
 * Resolves [RelativeDateStrings] from the app's `strings.xml` resources.
 * Uses [Resources] (not `stringResource`) so it can be called from plain code.
 */
fun relativeDateStrings(res: Resources): RelativeDateStrings = RelativeDateStrings(
    today = res.getString(R.string.date_today),
    yesterday = res.getString(R.string.date_yesterday),
    tomorrow = res.getString(R.string.date_tomorrow),
    daysAgo = { n -> res.getQuantityString(R.plurals.date_days_ago, n, n) },
    inDays = { n -> res.getQuantityString(R.plurals.date_in_days, n, n) },
    weeksAgo = { n -> res.getQuantityString(R.plurals.date_weeks_ago, n, n) },
    inWeeks = { n -> res.getQuantityString(R.plurals.date_in_weeks, n, n) },
    monthsAgo = { n -> res.getQuantityString(R.plurals.date_months_ago, n, n) },
    inMonths = { n -> res.getQuantityString(R.plurals.date_in_months, n, n) },
    yearsAgo = { n -> res.getQuantityString(R.plurals.date_years_ago, n, n) },
    inYears = { n -> res.getQuantityString(R.plurals.date_in_years, n, n) }
)

/**
 * Renders an event timestamp according to the user's [DateDisplay] preference.
 * Used by the events list, event detail, and contact detail event history so the
 * P1 preference is honored everywhere event dates appear.
 *
 * Pure function — the localized copy arrives via [strings] and the
 * SimpleDateFormat [absolutePattern] via the caller (both live in `strings.xml`,
 * see `date_pattern_event` / `date_pattern_event_full`).
 */
fun formatEventDate(
    timestamp: Long,
    dateDisplay: DateDisplay,
    strings: RelativeDateStrings,
    absolutePattern: String,
    now: Long = System.currentTimeMillis()
): String = when (dateDisplay) {
    DateDisplay.ABSOLUTE -> SimpleDateFormat(absolutePattern, Locale.getDefault())
        .format(Date(timestamp))

    DateDisplay.RELATIVE -> relativeDate(timestamp, strings, now)
}

/**
 * Composable convenience wrapper around [formatEventDate]: resolves the
 * [RelativeDateStrings] and the date pattern from `strings.xml`.
 *
 * @param absolutePatternRes string resource holding a SimpleDateFormat pattern
 *   (default `R.string.date_pattern_event`).
 */
@Composable
fun formatEventDateText(
    timestamp: Long,
    dateDisplay: DateDisplay,
    absolutePatternRes: Int = R.string.date_pattern_event,
    now: Long = System.currentTimeMillis()
): String {
    val strings = relativeDateStrings(LocalContext.current.resources)
    return formatEventDate(
        timestamp = timestamp,
        dateDisplay = dateDisplay,
        strings = strings,
        absolutePattern = stringResource(absolutePatternRes),
        now = now
    )
}

/**
 * "Today" / "Yesterday" / "3 days ago" / "In 2 weeks" style label for [timestamp]
 * relative to [now]. Pure function ([now] injectable) so it is JVM-unit-testable;
 * the actual wording comes from [strings] (`strings.xml`).
 */
fun relativeDate(
    timestamp: Long,
    strings: RelativeDateStrings,
    now: Long = System.currentTimeMillis()
): String {
    val days = localDayNumber(now) - localDayNumber(timestamp) // positive = in the past
    return when {
        days == 0L -> strings.today
        days == 1L -> strings.yesterday
        days == -1L -> strings.tomorrow
        days in 2L..6L -> strings.daysAgo(days.toInt())
        days in -6L..-2L -> strings.inDays((-days).toInt())
        days in 7L..29L -> strings.weeksAgo((days / 7).toInt())
        days in -29L..-7L -> strings.inWeeks((-days / 7).toInt())
        days in 30L..364L -> strings.monthsAgo((days / 30).toInt())
        days in -364L..-30L -> strings.inMonths((-days / 30).toInt())
        days >= 365L -> strings.yearsAgo((days / 365).toInt())
        else -> strings.inYears((-days / 365).toInt())
    }
}

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
