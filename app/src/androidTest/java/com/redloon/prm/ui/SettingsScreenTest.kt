package com.redloon.prm.ui

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.redloon.prm.data.settings.DateDisplay
import com.redloon.prm.data.settings.DefaultEventTime
import com.redloon.prm.data.settings.SettingsRepository
import com.redloon.prm.data.settings.ThemeMode
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the Settings screen (packet P1).
 *
 * Covers the packet's test plan: open via the gear icon (from BOTH list screens —
 * an acceptance criterion), toggle theme and assert the flip applies instantly
 * (rendered luminance changes without any restart) and survives an activity
 * relaunch (recreate), and every selector updates the stored preference.
 *
 * NOTE: the DataStore backing [SettingsRepository] is a process-wide Hilt
 * singleton shared by all tests in one instrumentation run, so setup resets every
 * P1 preference to its default before asserting.
 */
@HiltAndroidTest
class SettingsScreenTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            // Start clean: the settings DataStore is shared across tests in this run
            settingsRepository.setThemeMode(ThemeMode.SYSTEM)
            settingsRepository.setDynamicColor(true)
            settingsRepository.setDateDisplay(DateDisplay.ABSOLUTE)
            settingsRepository.setDefaultEventTime(DefaultEventTime.NOW)
            settingsRepository.setAutomaticContactSyncEnabled(false)
        }
    }

    @Test
    fun gearOnContactsScreen_opensSettings_andBackReturns() {
        composeTestRule.navigateToContactsTab()
        composeTestRule.waitUntilTagExists("settings_button")
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitUntilTagDisplayed("settings_screen")

        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitUntilTagDisplayed("contacts_list_screen")
    }

    @Test
    fun gearOnEventsScreen_opensSettings() {
        composeTestRule.navigateToHistory()
        composeTestRule.waitUntilTagExists("settings_button")
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitUntilTagDisplayed("settings_screen")
    }

    @Test
    fun themeModeChange_appliesInstantly_andSurvivesRelaunch() {
        openSettingsFromContacts()

        // Light: select and wait until the preference is actually stored.
        composeTestRule.waitUntilTagExists("theme_mode_light")
        composeTestRule.onNodeWithTag("theme_mode_light").performClick()
        awaitStored { runBlocking { settingsRepository.themeMode.first() } == ThemeMode.LIGHT }
        composeTestRule.onNodeWithTag("theme_mode_light").assertIsSelected()
        composeTestRule.waitForIdle()
        val lightLuminance = averageLuminance("settings_screen")

        // Dark: the rendered theme must flip immediately — no activity restart.
        composeTestRule.onNodeWithTag("theme_mode_dark").performClick()
        awaitStored { runBlocking { settingsRepository.themeMode.first() } == ThemeMode.DARK }
        composeTestRule.onNodeWithTag("theme_mode_dark").assertIsSelected()
        composeTestRule.waitForIdle()
        val darkLuminance = averageLuminance("settings_screen")

        assertTrue(
            "dark theme must render darker than light (dark=$darkLuminance light=$lightLuminance)",
            darkLuminance < lightLuminance - 0.2
        )

        // Relaunch: recreate the activity and assert the choice is still applied.
        composeTestRule.activityRule.scenario.recreate()
        ensureSettingsScreenShown()
        composeTestRule.waitUntilTagExists("theme_mode_dark")
        composeTestRule.onNodeWithTag("theme_mode_dark").assertIsSelected()
    }

    @Test
    fun preferenceSelectors_updateStoredValues() {
        openSettingsFromContacts()

        composeTestRule.waitUntilTagExists("date_display_selector")
        composeTestRule.onNodeWithTag("date_display_relative").performScrollTo().performClick()
        awaitStored {
            runBlocking { settingsRepository.dateDisplay.first() } == DateDisplay.RELATIVE
        }
        composeTestRule.onNodeWithTag("date_display_relative").assertIsSelected()

        composeTestRule.onNodeWithTag(
            "default_event_time_last_used"
        ).performScrollTo().performClick()
        awaitStored {
            runBlocking { settingsRepository.defaultEventTime.first() } ==
                DefaultEventTime.LAST_USED
        }
        composeTestRule.onNodeWithTag("default_event_time_last_used").assertIsSelected()

        composeTestRule.onNodeWithTag("dynamic_color_toggle").performScrollTo().performClick()
        awaitStored { runBlocking { settingsRepository.dynamicColor.first() } == false }
        composeTestRule.onNodeWithTag("dynamic_color_toggle").assertIsOff()
    }

    @Test
    fun automaticContactRefreshToggleReflectsDisabledSetting() {
        openSettingsFromContacts()
        composeTestRule.onNodeWithTag("automatic_contact_refresh_toggle")
            .performScrollTo()
        composeTestRule.onNodeWithTag("automatic_contact_refresh_toggle").assertIsOff()
    }

    // --- helpers ---

    private fun openSettingsFromContacts() {
        composeTestRule.navigateToContactsTab()
        composeTestRule.waitUntilTagExists("settings_button")
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitUntilTagDisplayed("settings_screen")
    }

    /** After recreate() the nav back stack may restore onto Settings directly. */
    private fun ensureSettingsScreenShown() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                try {
                    composeTestRule.onNodeWithTag("settings_button").assertExists()
                    true
                } catch (e2: AssertionError) {
                    false
                }
            }
        }
        try {
            composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        } catch (e: AssertionError) {
            composeTestRule.onNodeWithTag("settings_button").performClick()
            composeTestRule.waitUntilTagDisplayed("settings_screen")
        }
    }

    private fun awaitStored(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) { condition() }
    }

    /** Mean perceptual luminance (0..1) of a captured node, sampled every 16px. */
    private fun averageLuminance(tag: String): Double {
        val pixels = composeTestRule.onNodeWithTag(tag).captureToImage().toPixelMap()
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < pixels.height) {
            var x = 0
            while (x < pixels.width) {
                val color = pixels[x, y]
                sum += 0.2126 * color.red + 0.7152 * color.green + 0.0722 * color.blue
                count++
                x += 16
            }
            y += 16
        }
        return sum / count
    }
}
