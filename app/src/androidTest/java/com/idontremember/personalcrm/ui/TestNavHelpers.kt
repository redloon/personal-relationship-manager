package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/**
 * Shared navigation helpers for UI tests.
 *
 * Home and People retain separate histories. Reselecting a tab returns to its root.
 * Event history is a secondary Home action (nav_tab_events).
 */
fun ComposeTestRule.navigateToEventsTab() {
    onNodeWithTag("nav_tab_home").performClick()
    onNodeWithTag("nav_tab_home").performClick() // Reselect restores the Home root if its history was retained.
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
