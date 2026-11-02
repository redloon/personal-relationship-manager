package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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

    private fun openPicker(buttonTag: String, searchTag: String) {
        composeTestRule.waitUntilTagDisplayed("add_event_sheet")
        composeTestRule.waitUntilTagExists(buttonTag)
        composeTestRule.onNodeWithTag(buttonTag).performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitUntilTagDisplayed(searchTag)
    }

    private fun openPersonPicker() = openPicker("capture_people_button", "event_person_search")

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
        composeTestRule.onNodeWithTag("save_event_button").assertIsDisplayed().performClick()
        composeTestRule.waitUntilTagExists("event_save_error", 10_000)
        composeTestRule.waitUntilTagDisplayed("event_save_error", 10_000)
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed().performClick()
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
        composeTestRule.onNodeWithTag("save_event_button").assertIsDisplayed().performClick()
        composeTestRule.waitUntil(10_000) { runBlocking { factDao.getByContactIdOnce(id).any { it.text.contains("Likes tea") } } }
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
        composeTestRule.onNodeWithTag("save_event_button").assertIsDisplayed().performClick()

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
        composeTestRule.onNodeWithTag("save_event_button").assertIsDisplayed().performClick()
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
        openPersonPicker()
        composeTestRule.onNodeWithTag("event_person_search").performTextInput("Alice Dogfood")
        composeTestRule.waitUntilTagExists("event_person_option_$contactId")
        composeTestRule.onNodeWithTag("event_person_option_$contactId").performClick()
        composeTestRule.onNodeWithTag("save_event_edit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { eventRepository.getEventsForContact(contactId).first().any { it.note == note } }
        }
    }
    @Test
    fun factCanCreateANameOnlyPersonWithoutContactsPermission() {
        openAddEventSheet()
        composeTestRule.onNodeWithText("Fact").performClick()
        composeTestRule.onNodeWithTag("note_text_field").performTextInput("Enjoys climbing")
        openPersonPicker()
        composeTestRule.onNodeWithTag("event_person_search").performTextInput("Morgan Capture Test")
        composeTestRule.onNodeWithTag("capture_create_person").performClick()
        composeTestRule.waitUntilTagDisplayed("save_event_button")
        composeTestRule.onNodeWithTag("save_event_button").performClick()
        composeTestRule.waitUntil(10_000) {
            runBlocking {
                val person = contactDao.getAll().first().find { it.displayName == "Morgan Capture Test" }
                person != null && person.androidContactId == null && factDao.getByContactIdOnce(person.id).any { it.text == "Enjoys climbing" }
            }
        }
    }

    @Test
    fun editingTagsUsesSharedComposerAndKeepsEventDate() {
        val eventDate = 1_700_000_000_000L
        val id = runBlocking { eventRepository.createEvent("Original tagged note", emptyList(), listOf("before"), eventDate) }
        composeTestRule.navigateToEventsTab()
        composeTestRule.waitUntilTagDisplayed("events_list_screen")
        composeTestRule.onNodeWithText("Original tagged note").performClick()
        composeTestRule.waitUntilTagDisplayed("event_detail_screen")
        composeTestRule.onNodeWithTag("edit_event_button").performClick()
        composeTestRule.waitUntilTagDisplayed("add_event_sheet")
        openPicker("capture_tags_button", "capture_tag_search")
        composeTestRule.onNodeWithTag("capture_tag_search").performTextInput("after")
        composeTestRule.onNodeWithText("Add #after").performClick()
        composeTestRule.onNodeWithTag("save_event_edit_button").performClick()
        composeTestRule.waitUntil(10_000) { runBlocking { eventRepository.getEventWithMeta(id)?.tags?.any { it.name == "after" } == true } }
        org.junit.Assert.assertEquals(eventDate, runBlocking { eventRepository.getEventById(id)!!.eventDate })
    }

    @Test
    fun editingDraftDoesNotOverwriteNewCaptureDraft() {
        openAddEventSheet()
        composeTestRule.onNodeWithTag("note_text_field").performTextInput("Unfinished new note")
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        val id = runBlocking { eventRepository.createEvent("Existing note to edit", emptyList(), emptyList()) }
        composeTestRule.waitUntilTagDisplayed("events_list_screen")
        composeTestRule.onNodeWithText("Existing note to edit").performClick()
        composeTestRule.waitUntilTagDisplayed("event_detail_screen")
        composeTestRule.onNodeWithTag("edit_event_button").performClick()
        composeTestRule.waitUntilTagDisplayed("add_event_sheet")
        composeTestRule.onNodeWithTag("note_text_field").performTextInput(" appended")
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.waitUntilTagDisplayed("event_detail_screen")
        val prefs = composeTestRule.activity.getSharedPreferences("capture_draft", android.content.Context.MODE_PRIVATE)
        org.junit.Assert.assertTrue(prefs.getString("draft", "")!!.contains("Unfinished new note"))
        org.junit.Assert.assertTrue(prefs.getString("event_$id", "")!!.contains("appended"))
    }

    @Test
    fun saveStaysVisibleAboveKeyboardWhileEditingALongNote() {
        openAddEventSheet()
        composeTestRule.onNodeWithTag("note_text_field").performTextInput((1..20).joinToString("\n") { "A detail from this conversation $it" })
        composeTestRule.onNodeWithTag("note_text_field").assertIsFocused()
        composeTestRule.waitUntil(10_000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(composeTestRule.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        composeTestRule.onNodeWithTag("save_event_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_event_button").performClick()
        composeTestRule.waitUntil(10_000) { runBlocking { eventRepository.getAllEvents().first().any { it.note.contains("conversation 20") } } }
    }

    @Test
    fun profileCaptureExplainsDifferentDraftBeforeStartingForThatPerson() {
        val contactId = runBlocking { contactDao.insert(Contact(displayName = "Morgan Note Context")) }
        openAddEventSheet()
        composeTestRule.onNodeWithTag("note_text_field").performTextInput("A draft about something else")
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.navigateToContactsTab()
        composeTestRule.onNodeWithTag("search_field").performTextInput("Morgan Note Context")
        composeTestRule.onNode(hasTestTag("contact_list_item") and hasText("Morgan Note Context")).performClick()
        composeTestRule.waitUntilTagDisplayed("contact_detail_screen")
        composeTestRule.onNodeWithTag("person_add_note").performClick()
        composeTestRule.waitUntil(10_000) { composeTestRule.onAllNodesWithText("Discard draft and start new").fetchSemanticsNodes().isNotEmpty() }
        val prefs = composeTestRule.activity.getSharedPreferences("capture_draft", android.content.Context.MODE_PRIVATE)
        org.junit.Assert.assertTrue(prefs.getString("draft", "")!!.contains("A draft about something else"))
        composeTestRule.onNodeWithText("Discard draft and start new").performClick()
        composeTestRule.waitUntilTagDisplayed("note_text_field")
        composeTestRule.onNodeWithTag("note_text_field").performTextInput("Correct person's context")
        composeTestRule.onNodeWithTag("save_event_button").performClick()
        composeTestRule.waitUntil(10_000) { runBlocking { eventRepository.getEventsForContact(contactId).first().any { it.note == "Correct person's context" } } }
    }

    @Test
    fun deletingANoteCanBeUndoneWithoutLosingItsPeople() {
        val contactId = runBlocking { contactDao.insert(Contact(displayName = "Morgan Recovery")) }
        val eventId = runBlocking { eventRepository.createEvent("Keep this memory", listOf(contactId), listOf("recovery")) }
        composeTestRule.navigateToEventsTab()
        composeTestRule.onNodeWithText("Keep this memory").performClick()
        composeTestRule.waitUntilTagDisplayed("event_detail_screen")
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(com.idontremember.personalcrm.R.string.cd_delete_event)).performClick()
        composeTestRule.onNodeWithText("Delete", substring = false).performClick()
        composeTestRule.waitUntil(10_000) { composeTestRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitUntilTagDisplayed("edit_event_button")
        val restored = runBlocking { eventRepository.getEventWithMeta(eventId) }
        org.junit.Assert.assertEquals(listOf(contactId), restored!!.contacts.map { it.id })
        org.junit.Assert.assertEquals(listOf("recovery"), restored.tags.map { it.name })
    }

}
