package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * UI tests for the AddEventSheet.
 *
 * The sheet is a ModalBottomSheet opened from the Events screen FAB. Tests
 * reach it via the bottom nav bar ("nav_tab_events") + FAB ("add_event_fab")
 * — see TestNavHelpers.kt.
 *
 * NOTE: the in-memory test database is a process-wide Hilt singleton shared by
 * all tests in one instrumentation run, so setup clears every event before
 * asserting on the list contents.
 */
@HiltAndroidTest
class AddEventSheetTest {

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

    private fun openAddEventSheet() {
        composeTestRule.navigateToEventsTab()
        composeTestRule.waitUntilTagExists("add_event_fab")
        composeTestRule.onNodeWithTag("add_event_fab").performClick()
        composeTestRule.waitUntilTagDisplayed("add_event_sheet")
    }

    @Test
    fun addEventSheet_opensFromFab() {
        openAddEventSheet()
        composeTestRule.onNodeWithTag("note_text_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_event_button").assertIsDisplayed()
    }

    @Test
    fun addEventSheet_savesEvent_andShowsItInList() {
        val note = "Dentist appointment follow-up"
        openAddEventSheet()

        composeTestRule.onNodeWithTag("note_text_field").performTextInput(note)
        composeTestRule.onNodeWithTag("save_event_button").performClick()

        // Sheet dismisses and the saved event appears in the events list
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithText(note).assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }
}
