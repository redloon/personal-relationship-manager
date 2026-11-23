package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.idontremember.personalcrm.data.db.AppDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for complete capture History.
 *
 * Navigation: open History from Home (see TestNavHelpers.kt).
 *
 * NOTE: the in-memory test database is a process-wide Hilt singleton shared by
 * all tests in one instrumentation run. History includes notes, facts and follow-ups,
 * so setup clears the whole test database before checking its empty state.
 */
@HiltAndroidTest
class HistoryScreenTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: AppDatabase

    @Before
    fun setup() {
        hiltRule.inject()
        database.clearAllTables()
    }

    @Test
    fun eventsTab_showsEventsScreen() {
        composeTestRule.navigateToHistory()
        composeTestRule.onNodeWithTag("events_list_screen").assertIsDisplayed()
    }

    @Test
    fun eventsTab_showsSearchFieldAndFab() {
        composeTestRule.navigateToHistory()
        composeTestRule.onNodeWithTag("events_search_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_event_fab").assertIsDisplayed()
    }

    @Test
    fun history_showsEmptyStateWhenNoCapturedRecords() {
        composeTestRule.navigateToHistory()
        // P2: the shared EmptyState composable renders this — tag + localized text.
        composeTestRule.waitUntilTagDisplayed("events_empty_state")
        composeTestRule.onNodeWithTag("events_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("No saved records yet.").assertExists()
    }
}
