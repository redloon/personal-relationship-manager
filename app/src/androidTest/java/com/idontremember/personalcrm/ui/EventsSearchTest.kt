package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
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
 * UI tests for the Events search feature (filter events by note text).
 *
 * Navigation: tap the Events tab on the bottom nav bar (see TestNavHelpers.kt).
 *
 * NOTE: the in-memory test database is a process-wide Hilt singleton shared by
 * all tests in one instrumentation run, so setup clears every event before
 * seeding to keep each test deterministic.
 */
@HiltAndroidTest
class EventsSearchTest {

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
            eventRepository.createEvent(
                note = "Coffee with Alice about the new project",
                contactIds = emptyList(),
                tagNames = listOf("coffee"),
                eventDate = 1_700_000_000_000
            )
            eventRepository.createEvent(
                note = "Phone call with Bob re: contract",
                contactIds = emptyList(),
                tagNames = emptyList(),
                eventDate = 1_700_000_100_000
            )
        }
    }

    private fun navigateToEvents() {
        composeTestRule.navigateToEventsTab()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithTag("events_list").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun eventsTab_showsSeededEvents() {
        navigateToEvents()
        composeTestRule.onNodeWithText("Coffee with Alice about the new project").assertIsDisplayed()
        composeTestRule.onNodeWithText("Phone call with Bob re: contract").assertIsDisplayed()
    }

    @Test
    fun searchFiltersEvents_byNoteText() {
        navigateToEvents()
        composeTestRule.onNodeWithTag("events_search_field").performTextInput("coffee")

        // Non-matching event disappears from the list
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Phone call with Bob re: contract")
                .fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Coffee with Alice about the new project").assertIsDisplayed()
    }

    @Test
    fun searchWithNoMatches_showsEmptyState() {
        navigateToEvents()
        composeTestRule.onNodeWithTag("events_search_field").performTextInput("zzzznope")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithTag("events_empty_state").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        composeTestRule.onNodeWithText("No events match your search.", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun clearSearchRestoresEvents() {
        navigateToEvents()
        composeTestRule.onNodeWithTag("events_search_field").performTextInput("zzzznope")
        composeTestRule.waitUntilTagDisplayed("events_empty_state")
        composeTestRule.onNodeWithTag("clear_events_search_button").performClick()
        composeTestRule.waitUntilTagDisplayed("events_list")
        composeTestRule.onNodeWithText("Coffee with Alice about the new project").assertIsDisplayed()
        composeTestRule.onNodeWithText("Phone call with Bob re: contract").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_events_search_button").assertDoesNotExist()
    }
}
