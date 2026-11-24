package com.redloon.prm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.redloon.prm.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Smoke test: does the app launch without crashing?
 * This is the most critical test — if this fails, nothing else matters.
 */
@HiltAndroidTest
class AppLaunchTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun appLaunchesWithoutCrash() {
        // Just verify the activity created and is visible
        composeTestRule.activityRule.scenario
        composeTestRule.waitForIdle()
    }

    @Test
    fun appIdentityIsPrm() {
        val activity = composeTestRule.activity
        val label = activity.packageManager.getApplicationLabel(activity.applicationInfo).toString()

        assertEquals("com.redloon.prm", activity.packageName)
        assertEquals("PRM", label)
    }

    @Test
    fun dashboardIsDisplayed() {
        // The discovery dashboard is the initial route.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun dashboardExists() {
        // Verify the dashboard composable is rendered
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithTag("dashboard_screen").assertExists()
                true
            } catch (e: AssertionError) {
                // Retry after initial composition
                try {
                    composeTestRule.onNodeWithTag("dashboard_screen").assertExists()
                    true
                } catch (e2: AssertionError) {
                    false
                }
            }
        }
    }
}
