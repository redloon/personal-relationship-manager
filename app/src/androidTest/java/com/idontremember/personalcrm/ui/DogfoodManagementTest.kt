package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** Recheck older dogfood requests before recommending that the original notes be deleted. */
@HiltAndroidTest
class DogfoodManagementTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var db: AppDatabase
    @Before fun setup() {
        hilt.inject()
        db.clearAllTables()
        ui.activity.getSharedPreferences("capture_draft", 0).edit().clear().commit()
    }
    @After fun cleanup() { db.clearAllTables() }

    @Test fun deletingATagThroughManagementPreservesNoteProse() {
        val (tag, event) = runBlocking {
            val tag = db.tagDao().insert(Tag(name = "dogfood-tag"))
            val event = db.eventDao().insert(Event(note = "Lunch #dogfood-tag remains literal prose"))
            db.eventDao().insertEventTag(EventTag(event, tag))
            tag to event
        }
        ui.waitUntilTagDisplayed("tags_shortcut")
        ui.onNodeWithTag("tags_shortcut").performClick()
        ui.waitUntilTagDisplayed("tag_$tag")
        ui.onNodeWithContentDescription(ui.activity.getString(R.string.search_delete_tag, "dogfood-tag")).performClick()
        ui.onNodeWithText("Delete", substring = false).performClick()
        ui.waitUntil(5_000) { runBlocking { db.tagDao().getAllOnce().none { it.id == tag } } }
        ui.onNodeWithTag("tag_$tag").assertDoesNotExist()
        assertEquals("Lunch #dogfood-tag remains literal prose", runBlocking { db.eventDao().getById(event)!!.note })
        assertTrue(runBlocking { db.eventDao().getTagIdsForEvent(event).isEmpty() })
    }

    @Test fun relationshipSearchNarrowsAndSelectsTheIntendedPerson() {
        val (maya, alex) = runBlocking {
            val maya = db.contactDao().insert(Contact(displayName = "Maya Search"))
            val alex = db.contactDao().insert(Contact(displayName = "Alex Rivera"))
            db.contactDao().insert(Contact(displayName = "Unrelated Person"))
            maya to alex
        }
        ui.navigateToContactsTab()
        ui.onNodeWithTag("person_row_$maya").performTouchInput { click() }
        ui.waitUntilTagDisplayed("contact_detail_screen")
        ui.onNodeWithTag("contact_detail_screen").performScrollToNode(hasTestTag("person_management"))
        ui.onNodeWithTag("person_management").performClick()
        ui.onNodeWithTag("contact_detail_screen").performScrollToNode(hasTestTag("relationship_add"))
        ui.onNodeWithTag("relationship_add").performClick()
        ui.onNodeWithTag("relationship_label").performTextInput("Spouse")
        ui.onNode(hasSetTextAction() and hasText("Find a person")).performTextInput("Nobody")
        ui.onNodeWithText("Alex Rivera").assertDoesNotExist()
        ui.onNodeWithTag("clear_relationship_search").performClick()
        ui.onNode(hasSetTextAction() and hasText("Find a person")).performTextInput("rIvErA")
        ui.waitForKeyboard(true)
        ui.onNodeWithText("Unrelated Person").assertDoesNotExist()
        ui.onNodeWithText("Alex Rivera").performClick()
        ui.onNodeWithTag("relationship_confirm").performClick()
        ui.waitUntil(5_000) { runBlocking { db.profileLinksDao().allRelationships().any {
            setOf(it.contactA, it.contactB) == setOf(maya, alex) && it.label == "Spouse"
        } } }
    }
}
