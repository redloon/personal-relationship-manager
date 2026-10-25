package com.idontremember.personalcrm.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** How the app decides between light and dark color schemes. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How event timestamps are rendered: fixed pattern vs. relative to now. */
enum class DateDisplay { ABSOLUTE, RELATIVE }

/** What timestamp a new event's date/time pickers default to. */
enum class DefaultEventTime { NOW, LAST_USED }

/**
 * Typed settings facade over a Hilt-provided Preferences DataStore singleton.
 *
 * Every preference is exposed as a typed [Flow] plus a suspend setter. Keys live in
 * [SettingsKeys] as named constants — never stringly-typed at call sites. Later
 * packets (e.g. P10 notifications) add their own keys next to the existing ones and
 * hang new typed flows/setters off this class; nothing else needs to change.
 */
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        enumValueOrDefault(prefs[SettingsKeys.THEME_MODE], ThemeMode.SYSTEM)
    }

    val dynamicColor: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SettingsKeys.DYNAMIC_COLOR] ?: true
    }

    val dateDisplay: Flow<DateDisplay> = dataStore.data.map { prefs ->
        enumValueOrDefault(prefs[SettingsKeys.DATE_DISPLAY], DateDisplay.ABSOLUTE)
    }

    val defaultEventTime: Flow<DefaultEventTime> = dataStore.data.map { prefs ->
        enumValueOrDefault(prefs[SettingsKeys.DEFAULT_EVENT_TIME], DefaultEventTime.NOW)
    }

    /** Last timestamp used when saving an event; 0L when none was recorded yet. */
    val lastUsedEventTimestamp: Flow<Long> = dataStore.data.map { prefs ->
        prefs[SettingsKeys.LAST_USED_EVENT_TIMESTAMP] ?: 0L
    }

    suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { it[SettingsKeys.THEME_MODE] = value.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDateDisplay(value: DateDisplay) {
        dataStore.edit { it[SettingsKeys.DATE_DISPLAY] = value.name }
    }

    suspend fun setDefaultEventTime(value: DefaultEventTime) {
        dataStore.edit { it[SettingsKeys.DEFAULT_EVENT_TIME] = value.name }
    }

    suspend fun setLastUsedEventTimestamp(value: Long) {
        dataStore.edit { it[SettingsKeys.LAST_USED_EVENT_TIMESTAMP] = value }
    }
}

/**
 * Named preference keys. Add new keys here (typed helpers on [SettingsRepository]),
 * never inline `stringPreferencesKey("...")` at call sites.
 */
object SettingsKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val DATE_DISPLAY = stringPreferencesKey("date_display")
    val DEFAULT_EVENT_TIME = stringPreferencesKey("default_event_time")
    val LAST_USED_EVENT_TIMESTAMP = longPreferencesKey("last_used_event_timestamp")
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T =
    raw?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
