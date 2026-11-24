package com.redloon.prm.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Shared navigation helpers for UI tests.
 *
 * Home and People retain separate histories. Reselecting a tab returns to its root.
 * Event history is a secondary Home action (nav_tab_events).
 */
fun ComposeTestRule.navigateToHistory() {
    onNodeWithTag("nav_tab_home").performClick()
    // Reselect restores the Home root if its history was retained.
    onNodeWithTag("nav_tab_home").performClick()
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

/** Dialog sheets own a different window from the Activity; inspect the actual IME. */
fun ComposeTestRule.waitForKeyboard(visible: Boolean, timeoutMillis: Long = 10_000) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val originalFlags = automation.serviceInfo.flags
    try {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        waitUntil(timeoutMillis) {
            automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ==
                visible
        }
        waitForIdle()
    } finally {
        automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
    }
}
