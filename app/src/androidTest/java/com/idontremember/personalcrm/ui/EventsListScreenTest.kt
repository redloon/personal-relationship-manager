package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.idontremember.personalcrm.data.repository.EventRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * UI tests for the EventsListScreen.
 *
 * Navigation: tap the Events tab on the bottom nav bar (see TestNavHelpers.kt).
 *
 * NOTE: the in-memory test database is a process-wide Hilt singleton shared by
 * all tests in one instrumentation run, so setup clears every event to keep the
 * empty/list assertions deterministic.
 */
@HiltAndroidTest
class EventsListScreenTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var eventRepository: EventRepository

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            // Start clean: the test DB singleton is shared across tests in this run
            eventRepository.getAllEvents().first().forEach { eventRepository.deleteEvent(it) }
        }
    }

    @Test
    fun eventsTab_showsEventsScreen() {
        composeTestRule.navigateToEventsTab()
        composeTestRule.onNodeWithTag("events_list_screen").assertIsDisplayed()
    }

    @Test
    fun eventsTab_showsSearchFieldAndFab() {
        composeTestRule.navigateToEventsTab()
        composeTestRule.onNodeWithTag("events_search_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_event_fab").assertIsDisplayed()
    }

    @Test
    fun eventsTab_showsEmptyStateWhenNoEvents() {
        composeTestRule.navigateToEventsTab()
        // P2: the shared EmptyState composable renders this — tag + localized text.
        composeTestRule.waitUntilTagDisplayed("events_empty_state")
        composeTestRule.onNodeWithTag("events_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("No notes yet.", substring = true).assertExists()
    }
}
