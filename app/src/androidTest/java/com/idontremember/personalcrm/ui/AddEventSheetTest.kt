package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.idontremember.personalcrm.data.repository.EventRepository
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.dao.ContactFactDao
import com.idontremember.personalcrm.data.db.entity.Contact
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
    @Inject lateinit var contactDao: ContactDao
    @Inject lateinit var factDao: ContactFactDao

    @Before
    fun setup() {
        hiltRule.inject()
        composeTestRule.activity.getSharedPreferences("capture_draft", android.content.Context.MODE_PRIVATE).edit().clear().commit()
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
    fun draft_survivesDismissal_andDiscardClearsIt() {
        openAddEventSheet()
        composeTestRule.onNodeWithTag("note_text_field").performTextInput("A draft to remember")
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.waitUntilTagDisplayed("add_event_fab")
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitUntilTagDisplayed("add_event_fab")
        composeTestRule.onNodeWithTag("add_event_fab").performClick()
        composeTestRule.waitUntilTagDisplayed("add_event_sheet")
        composeTestRule.onNodeWithTag("note_text_field").assertTextContains("A draft to remember")
        composeTestRule.onNodeWithText("Discard").performScrollTo().performClick()
        composeTestRule.waitUntilTagDisplayed("add_event_fab")
        org.junit.Assert.assertFalse("Discard must clear the persisted draft", composeTestRule.activity.getSharedPreferences("capture_draft", android.content.Context.MODE_PRIVATE).contains("draft"))
        composeTestRule.onNodeWithTag("add_event_fab").performClick()
        composeTestRule.waitUntilTagDisplayed("add_event_sheet")
        composeTestRule.onNodeWithTag("note_text_field").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        composeTestRule.onNodeWithText("Discard").performScrollTo().performClick()
    }

    @Test
    fun openDraftSurvivesActivityRecreation() {
        openAddEventSheet()
        composeTestRule.onNodeWithTag("note_text_field").performTextInput("Open draft")
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitUntilTagDisplayed("add_event_sheet", 10_000)
        composeTestRule.onNodeWithTag("note_text_field").assertTextContains("Open draft")
        composeTestRule.onNodeWithText("Discard").performScrollTo().performClick()
    }

    @Test
    fun failedSaveKeepsDraft() {
        val id = runBlocking { contactDao.insert(Contact(displayName = "Temporary Person")) }
        openAddEventSheet()
        composeTestRule.onNodeWithTag("note_text_field").performTextInput("Remember @Temporary")
        composeTestRule.waitUntil(10_000) { composeTestRule.onAllNodesWithText("Temporary Person").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithText("Temporary Person").performClick()
        runBlocking { contactDao.getById(id)?.let { contactDao.delete(it) } }
        composeTestRule.onNodeWithTag("save_event_button").performScrollTo().performClick()
        composeTestRule.waitUntilTagExists("event_save_error", 10_000)
        composeTestRule.waitUntilTagDisplayed("event_save_error", 10_000)
        composeTestRule.onNodeWithContentDescription("Close").performScrollTo().performClick()
        composeTestRule.waitUntilTagDisplayed("add_event_fab")
        composeTestRule.onNodeWithTag("add_event_fab").performClick()
        composeTestRule.waitUntilTagDisplayed("add_event_sheet")
        composeTestRule.onNodeWithTag("note_text_field").assertTextContains("Remember @Temporary Person ")
        composeTestRule.onNodeWithText("Discard").performScrollTo().performClick()
    }

    @Test
    fun factCaptureRequiresPersonAndSavesFact() {
        val id = runBlocking { contactDao.insert(Contact(displayName = "Fact Person")) }
        openAddEventSheet()
        composeTestRule.onNodeWithText("Fact").performClick()
        composeTestRule.onNodeWithTag("note_text_field").performTextInput("Likes tea ")
        composeTestRule.onNodeWithTag("save_event_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("note_text_field").performTextInput("@Fact")
        composeTestRule.waitUntil(10_000) { composeTestRule.onAllNodesWithText("Fact Person").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithText("Fact Person").performClick()
        composeTestRule.onNodeWithTag("save_event_button").performScrollTo().performClick()
        composeTestRule.waitUntil(10_000) { runBlocking { factDao.getByContactIdOnce(id).any { it.text.contains("Likes tea") } } }
    }

    @Test
    fun addEventSheet_opensFromFab() {
        openAddEventSheet()
        composeTestRule.onNodeWithTag("note_text_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_event_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun addEventSheet_savesEvent_andShowsItInList() {
        val note = "Dentist appointment follow-up"
        openAddEventSheet()

        composeTestRule.onNodeWithTag("note_text_field").performTextInput(note)
        composeTestRule.onNodeWithTag("save_event_button").performScrollTo().performClick()

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

    @Test
    fun unlinkedQuickNote_canBeLinkedLaterAndRecalledFromContact() {
        val contactId = runBlocking { contactDao.insert(Contact(displayName = "Alice Dogfood")) }
        val note = "Remember Alice's new job"
        openAddEventSheet()
        composeTestRule.onNodeWithTag("note_text_field").performTextInput(note)
        composeTestRule.onNodeWithTag("save_event_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { eventRepository.getAllEvents().first().any { it.note == note } }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try { composeTestRule.onNodeWithTag("add_event_sheet").assertDoesNotExist(); true }
            catch (_: AssertionError) { false }
        }
        composeTestRule.waitUntilTagDisplayed("events_list_screen")
        composeTestRule.onNodeWithText(note).performClick()
        composeTestRule.waitUntilTagDisplayed("event_detail_screen")
        composeTestRule.onNodeWithTag("edit_event_button").performClick()
        composeTestRule.onNodeWithTag("event_person_search").performTextInput("Alice Dogfood")
        composeTestRule.waitUntilTagExists("event_person_option_$contactId")
        composeTestRule.onNodeWithTag("event_person_option_$contactId").performClick()
        composeTestRule.onNodeWithTag("save_event_edit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { eventRepository.getEventsForContact(contactId).first().any { it.note == note } }
        }
    }
}
