package com.redloon.prm.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.redloon.prm.data.contacts.PeopleListScope
import com.redloon.prm.data.contacts.PeopleSort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val contactImportOnboardingDismissed: Flow<Boolean> = dataStore.data.map {
        it[SettingsKeys.CONTACT_IMPORT_ONBOARDING_DISMISSED] ?: false
    }
    val automaticContactSyncEnabled: Flow<Boolean> = dataStore.data.map {
        it[SettingsKeys.AUTOMATIC_CONTACT_SYNC_ENABLED] ?: false
    }
    val lastSuccessfulContactSyncAt: Flow<Long> = dataStore.data.map {
        it[SettingsKeys.LAST_SUCCESSFUL_CONTACT_SYNC_AT] ?: 0L
    }

    suspend fun setContactImportOnboardingDismissed(dismissed: Boolean = true) {
        dataStore.edit { it[SettingsKeys.CONTACT_IMPORT_ONBOARDING_DISMISSED] = dismissed }
    }

    suspend fun setAutomaticContactSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.AUTOMATIC_CONTACT_SYNC_ENABLED] = enabled }
    }

    suspend fun setLastSuccessfulContactSyncAt(timestamp: Long) {
        dataStore.edit { it[SettingsKeys.LAST_SUCCESSFUL_CONTACT_SYNC_AT] = timestamp }
    }

    fun peopleSort(scope: PeopleListScope): Flow<PeopleSort> = dataStore.data.map { prefs ->
        enumValueOrDefault(prefs[SettingsKeys.peopleSort(scope)], PeopleSort.RECENT)
    }
    suspend fun setPeopleSort(scope: PeopleListScope, value: PeopleSort) {
        dataStore.edit { it[SettingsKeys.peopleSort(scope)] = value.name }
    }

    val followUpReminderPrompted: Flow<Boolean> = dataStore.data.map {
        it[SettingsKeys.FOLLOW_UP_REMINDER_PROMPTED]
            ?: false
    }
    suspend fun setFollowUpReminderPrompted(prompted: Boolean = true) {
        dataStore.edit {
            it[SettingsKeys.FOLLOW_UP_REMINDER_PROMPTED] =
                prompted
        }
    }

    val followUpRemindersEnabled: Flow<Boolean> = dataStore.data.map {
        it[SettingsKeys.FOLLOW_UP_REMINDERS_ENABLED]
            ?: false
    }
    val reminderHour: Flow<Int> = dataStore.data.map {
        (it[SettingsKeys.REMINDER_HOUR] ?: 9L).toInt().coerceIn(6, 19)
    }
    val lastReminderDay: Flow<String?> = dataStore.data.map { it[SettingsKeys.LAST_REMINDER_DAY] }

    suspend fun setFollowUpRemindersEnabled(enabled: Boolean) {
        dataStore.edit {
            it[SettingsKeys.FOLLOW_UP_REMINDERS_ENABLED] =
                enabled
        }
    }
    suspend fun setReminderHour(hour: Int) {
        dataStore.edit {
            it[SettingsKeys.REMINDER_HOUR] =
                hour.coerceIn(6, 19).toLong()
        }
    }
    suspend fun setLastReminderDay(day: String?) {
        dataStore.edit {
            if (day ==
                null
            ) {
                it.remove(SettingsKeys.LAST_REMINDER_DAY)
            } else {
                it[SettingsKeys.LAST_REMINDER_DAY] =
                    day
            }
        }
    }

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
    private val peopleSortKeys = PeopleListScope.entries.associateWith {
        stringPreferencesKey("people_sort_${it.name.lowercase(java.util.Locale.ROOT)}")
    }
    fun peopleSort(scope: PeopleListScope) = peopleSortKeys.getValue(scope)

    val FOLLOW_UP_REMINDER_PROMPTED = booleanPreferencesKey("followup_reminder_prompted")
    val FOLLOW_UP_REMINDERS_ENABLED = booleanPreferencesKey("followup_reminders_enabled")
    val REMINDER_HOUR = longPreferencesKey("reminder_hour")
    val LAST_REMINDER_DAY = stringPreferencesKey("last_reminder_day")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val DATE_DISPLAY = stringPreferencesKey("date_display")
    val DEFAULT_EVENT_TIME = stringPreferencesKey("default_event_time")
    val LAST_USED_EVENT_TIMESTAMP = longPreferencesKey("last_used_event_timestamp")
    val CONTACT_IMPORT_ONBOARDING_DISMISSED =
        booleanPreferencesKey("contact_import_onboarding_dismissed")
    val AUTOMATIC_CONTACT_SYNC_ENABLED =
        booleanPreferencesKey("automatic_contact_sync_enabled")
    val LAST_SUCCESSFUL_CONTACT_SYNC_AT =
        longPreferencesKey("last_successful_contact_sync_at")
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T =
    raw?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
