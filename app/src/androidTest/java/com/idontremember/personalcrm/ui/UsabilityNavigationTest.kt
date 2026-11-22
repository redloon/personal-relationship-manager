package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** End-to-end contracts between search, routes and read-only destinations. */
@HiltAndroidTest
class UsabilityNavigationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var db: AppDatabase

    @Before fun setup() {
        hilt.inject()
        db.clearAllTables()
        ui.activity.getSharedPreferences("capture_draft", 0).edit().clear().commit()
    }

    @Test fun tagSearchOpensPersonlessNotesAndReturnsToSearch() {
        val (tagId, eventId) = runBlocking {
            val tag = db.tagDao().insert(Tag(name = "dog-owner"))
            val event = db.eventDao().insert(Event(note = "Walking the dog at dawn"))
            db.eventDao().insertEventTag(EventTag(event, tag))
            tag to event
        }
        ui.waitUntilTagDisplayed("dashboard_search", 10_000)
        ui.onNodeWithTag("dashboard_search").performTextInput("dog-owner")
        ui.onNodeWithTag("recall_filter_tag").performScrollTo().performClick()
        ui.waitUntilTagDisplayed("dashboard_tag_$tagId", 10_000)
        ui.onNodeWithTag("dashboard_tag_$tagId").performClick()
        ui.waitUntilTagDisplayed("tag_detail_screen", 10_000)
        ui.onNodeWithTag("tag_note_$eventId").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("recall_event_$eventId").performClick()
        ui.waitUntilTagDisplayed("event_detail_screen", 10_000)
        ui.onNodeWithText("Walking the dog at dawn").assertIsDisplayed()
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntilTagDisplayed("tag_detail_screen", 10_000)
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntilTagDisplayed("dashboard_search", 10_000)
        ui.onNodeWithTag("dashboard_search").assertTextContains("dog-owner", substring = true)
    }

    @Test fun historyFactOpensReaderBeforeExplicitEditing() {
        val factId = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "History Person"))
            db.contactFactDao().insert(ContactFact(contactId = person, text = "Collects antique maps"))
        }
        ui.navigateToEventsTab()
        ui.waitUntilTagDisplayed("recall_fact_$factId", 10_000)
        ui.onNodeWithTag("recall_fact_$factId").performClick()
        ui.waitUntilTagDisplayed("fact_reader", 10_000)
        ui.onNodeWithTag("add_event_sheet").assertDoesNotExist()
        ui.onNodeWithTag("fact_reader_edit").performClick()
        ui.waitUntilTagDisplayed("add_event_sheet", 10_000)
        ui.onNodeWithTag("note_text_field").assertTextContains("Collects antique maps", substring = true)
    }

    @Test fun peopleDirectoryKeepsItsQueryAcrossNavigation() {
        runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Maya Context"))
            val event = db.eventDao().insert(Event(note = "We talked for ages. ".repeat(30) + "Priya is recovering well."))
            db.eventDao().insertEventContact(EventContact(event, person))
        }
        ui.navigateToContactsTab()
        ui.onNodeWithTag("search_field").performTextInput("Maya")
        ui.waitUntilTagDisplayed("contact_list_item", 10_000)
        ui.onNodeWithTag("contact_list_item").assertTextContains("Maya", substring = true)
        ui.onNodeWithTag("contact_list_item").performClick()
        ui.waitUntilTagDisplayed("person_search", 10_000)
        ui.onNodeWithTag("person_search").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntilTagDisplayed("search_field", 10_000)
        ui.onNodeWithTag("search_field").assertTextContains("Maya", substring = true)
    }
}
