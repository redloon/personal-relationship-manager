package com.redloon.prm.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.redloon.prm.data.contacts.ProfilePair
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * JVM unit tests for [SettingsRepository] against a real (file-backed) Preferences
 * DataStore in a temp directory — no emulator, no Robolectric.
 *
 * "Survives process death and reboot" is covered here at the persistence layer:
 * [valuesSurviveDataStoreRestart] closes the DataStore (scope cancelled, like a dead
 * process) and reads the values back from a fresh DataStore over the same file.
 * UI-level persistence is asserted in SettingsScreenTest (activity recreate).
 */
class SettingsRepositoryTest {

    @TempDir
    lateinit var tempFolder: File

    private val openScopes = mutableListOf<CoroutineScope>()

    @AfterEach
    fun tearDown() {
        openScopes.forEach { it.cancel() }
        openScopes.clear()
    }

    private fun newStore(file: File): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        openScopes.add(scope)
        return openDataStore(file, scope)
    }

    /** Opens a DataStore, retrying briefly while a previous instance winds down. */
    private fun openDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> {
        var lastError: IllegalStateException? = null
        repeat(50) {
            try {
                return PreferenceDataStoreFactory.create(scope = scope) { file }
            } catch (e: IllegalStateException) {
                lastError = e
                Thread.sleep(20)
            }
        }
        throw AssertionError("Could not open DataStore for $file", lastError)
    }

    private fun newFile(name: String): File = File(tempFolder, name)

    @Test
    fun reminderOptInIsExplicitAndDecliningDoesNotKeepPrompting() = runBlocking {
        val repository = SettingsRepository(newStore(newFile("reminders.preferences_pb")))
        assertEquals(false, repository.followUpRemindersEnabled.first())
        assertEquals(false, repository.followUpReminderPrompted.first())
        assertEquals(9, repository.reminderHour.first())
        repository.setFollowUpReminderPrompted()
        assertEquals(true, repository.followUpReminderPrompted.first())
        assertEquals(false, repository.followUpRemindersEnabled.first())
        repository.setFollowUpRemindersEnabled(true)
        repository.setReminderHour(100)
        assertEquals(true, repository.followUpRemindersEnabled.first())
        assertEquals(19, repository.reminderHour.first())
        repository.setReminderHour(-5)
        assertEquals(6, repository.reminderHour.first())
    }

    @Test
    fun contactImportChoiceAndRefreshStateAreExplicitAndPersistent() = runBlocking {
        val repository = SettingsRepository(newStore(newFile("contacts.preferences_pb")))

        assertEquals(false, repository.contactImportOnboardingDismissed.first())
        assertEquals(false, repository.automaticContactSyncEnabled.first())
        assertEquals(0L, repository.lastSuccessfulContactSyncAt.first())

        repository.setContactImportOnboardingDismissed()
        repository.setAutomaticContactSyncEnabled(true)
        repository.setLastSuccessfulContactSyncAt(1_700_000_000_000L)

        assertEquals(true, repository.contactImportOnboardingDismissed.first())
        assertEquals(true, repository.automaticContactSyncEnabled.first())
        assertEquals(1_700_000_000_000L, repository.lastSuccessfulContactSyncAt.first())
    }

    @Test
    fun dismissedReconnectPairsPersistAndCanBeClearedAfterRestore() = runBlocking {
        val repository = SettingsRepository(newStore(newFile("reconnect.preferences_pb")))
        val first = ProfilePair(10, 20)
        val second = ProfilePair(30, 40)

        assertTrue(repository.dismissedReconnectPairs.first().isEmpty())
        repository.dismissReconnectPair(first)
        repository.dismissReconnectPair(second)
        repository.dismissReconnectPair(first)
        assertEquals(setOf(first, second), repository.dismissedReconnectPairs.first())

        repository.clearDismissedReconnectPairs()
        assertTrue(repository.dismissedReconnectPairs.first().isEmpty())
    }

    @Test
    fun defaultsMatchDocumentedDefaults() = runBlocking {
        val repository = SettingsRepository(newStore(newFile("defaults.preferences_pb")))

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        assertEquals(true, repository.dynamicColor.first())
        assertEquals(DateDisplay.ABSOLUTE, repository.dateDisplay.first())
        assertEquals(DefaultEventTime.NOW, repository.defaultEventTime.first())
        assertEquals(0L, repository.lastUsedEventTimestamp.first())
        assertEquals(false, repository.contactImportOnboardingDismissed.first())
        assertEquals(false, repository.automaticContactSyncEnabled.first())
        assertEquals(0L, repository.lastSuccessfulContactSyncAt.first())
    }

    @Test
    fun settersRoundTripThroughDataStore() = runBlocking {
        val repository = SettingsRepository(newStore(newFile("roundtrip.preferences_pb")))

        repository.setThemeMode(ThemeMode.DARK)
        repository.setDynamicColor(false)
        repository.setDateDisplay(DateDisplay.RELATIVE)
        repository.setDefaultEventTime(DefaultEventTime.LAST_USED)
        repository.setLastUsedEventTimestamp(1_700_000_000_000L)

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
        assertEquals(false, repository.dynamicColor.first())
        assertEquals(DateDisplay.RELATIVE, repository.dateDisplay.first())
        assertEquals(DefaultEventTime.LAST_USED, repository.defaultEventTime.first())
        assertEquals(1_700_000_000_000L, repository.lastUsedEventTimestamp.first())
    }

    @Test
    fun valuesSurviveDataStoreRestart() {
        val file = newFile("restart.preferences_pb")

        // "Process 1": write settings, then die (scope cancelled, DataStore closed).
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store1 = PreferenceDataStoreFactory.create(scope = scope1) { file }
        runBlocking {
            val repository = SettingsRepository(store1)
            repository.setThemeMode(ThemeMode.LIGHT)
            repository.setDynamicColor(false)
            repository.setDateDisplay(DateDisplay.RELATIVE)
            repository.setDefaultEventTime(DefaultEventTime.LAST_USED)
            repository.setLastUsedEventTimestamp(1_234L)
            repository.setContactImportOnboardingDismissed()
            repository.setAutomaticContactSyncEnabled(true)
            repository.setLastSuccessfulContactSyncAt(5_678L)
        }
        scope1.cancel()
        runBlocking { scope1.coroutineContext.job.join() }
        // Note: unregistration of the old instance can lag; openDataStore retries.
        assertTrue(file.exists() && file.length() > 0, "preferences file should exist after writes")

        // "Process 2": fresh DataStore over the same file must see the same values.
        val repository = SettingsRepository(newStore(file))
        runBlocking {
            withTimeout(10_000) {
                assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
                assertEquals(false, repository.dynamicColor.first())
                assertEquals(DateDisplay.RELATIVE, repository.dateDisplay.first())
                assertEquals(DefaultEventTime.LAST_USED, repository.defaultEventTime.first())
                assertEquals(1_234L, repository.lastUsedEventTimestamp.first())
                assertEquals(true, repository.contactImportOnboardingDismissed.first())
                assertEquals(true, repository.automaticContactSyncEnabled.first())
                assertEquals(5_678L, repository.lastSuccessfulContactSyncAt.first())
            }
        }
    }

    @Test
    fun unknownStoredEnumValueFallsBackToDefault() = runBlocking {
        val file = newFile("garbage.preferences_pb")
        val store = newStore(file)
        // Simulate a value written by a future/removed enum member.
        store.edit { it[SettingsKeys.THEME_MODE] = "SOMETHING_ELSE" }
        store.edit { it[SettingsKeys.DATE_DISPLAY] = "ALSO_UNKNOWN" }

        val repository = SettingsRepository(store)
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        assertEquals(DateDisplay.ABSOLUTE, repository.dateDisplay.first())
    }
}
