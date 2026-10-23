package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/**
 * Shared navigation helpers for UI tests.
 *
 * The bottom navigation bar (testTags "nav_tab_contacts" / "nav_tab_events") is
 * the supported way for tests to reach top-level screens: the app starts on the
 * Contacts route, and clicking a tab clears the back stack to that tab's
 * top-level route.
 */
fun ComposeTestRule.navigateToEventsTab() {
    waitUntilTagExists("nav_tab_events")
    onNodeWithTag("nav_tab_events").performClick()
    waitUntilTagDisplayed("events_list_screen")
}

fun ComposeTestRule.navigateToContactsTab() {
    waitUntilTagExists("nav_tab_contacts")
    onNodeWithTag("nav_tab_contacts").performClick()
    waitUntilTagDisplayed("contacts_list_screen")
}

fun ComposeTestRule.waitUntilTagExists(tag: String, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis = timeoutMillis) {
        try {
            onNodeWithTag(tag).assertExists()
            true
        } catch (e: AssertionError) {
            false
        }
    }
}

fun ComposeTestRule.waitUntilTagDisplayed(tag: String, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis = timeoutMillis) {
        try {
            onNodeWithTag(tag).assertIsDisplayed()
            true
        } catch (e: AssertionError) {
            false
        }
    }
}
