package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for navigation.
 *
 * Verifies the app starts on the Contacts route (first in the nav backstack)
 * and that the initial screen renders without crashing.
 *
 * NOTE: Full navigation tests (switching screens, back stack behavior) require
 * either a bottom nav bar test hook or a way to set initial routes in tests.
 * A future agent should add a NavigationTestRule that allows starting from
 * arbitrary routes.
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
    fun appStartsOnContactsScreen() {
        // The app should start on the Contacts route (first in the nav backstack)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithText("Contacts").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun contactsScreenRendersFully() {
        // Verify the contacts screen renders all expected elements
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithText("Contacts").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        // Verify search field is present
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            try {
                composeTestRule.onNodeWithText("Search contacts...").assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        composeTestRule.waitForIdle()
    }
}