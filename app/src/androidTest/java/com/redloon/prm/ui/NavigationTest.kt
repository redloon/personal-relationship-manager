package com.redloon.prm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for navigation.
 *
 * Verifies the app starts on the dashboard,
 * that the initial screen renders without crashing, and that the bottom nav
 * bar switches between the top-level screens. Shared helpers for tab
 * navigation live in TestNavHelpers.kt.
 */
@HiltAndroidTest
class NavigationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun appStartsOnDashboard() {
        // The dashboard is the initial route.
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
    fun contactsScreenRendersFully() {
        composeTestRule.navigateToContactsTab()
        // Verify the contacts screen renders all expected elements
        // (testTag instead of the ambiguous "Contacts" text — see above)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithTag("contacts_list_screen").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        // Verify search field is present
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            try {
                composeTestRule.onNodeWithTag("search_field").assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun bottomNav_switchesBetweenTopLevelScreens() {
        // Tabs clear the back stack to the target top-level route
        composeTestRule.navigateToHistory()
        composeTestRule.onNodeWithTag("events_list_screen").assertIsDisplayed()

        composeTestRule.navigateToContactsTab()
        composeTestRule.onNodeWithTag("contacts_list_screen").assertIsDisplayed()
    }

    @Test
    fun reselectingHomeReturnsFromHistoryAndKeepsSearch() {
        composeTestRule.waitUntilTagDisplayed("dashboard_search")
        composeTestRule.onNodeWithTag("dashboard_search").performTextInput("climbing")
        composeTestRule.onNodeWithTag("nav_tab_events").performClick()
        composeTestRule.waitUntilTagDisplayed("events_list_screen")
        composeTestRule.onNodeWithTag("nav_tab_home").performClick()
        composeTestRule.waitUntilTagDisplayed("dashboard_search")
        composeTestRule.onNodeWithTag(
            "dashboard_search"
        ).assertTextContains("climbing", substring = true)
    }
}
