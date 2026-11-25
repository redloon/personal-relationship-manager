package com.redloon.prm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.db.entity.ContactFact
import com.redloon.prm.data.db.entity.ContactTag
import com.redloon.prm.data.db.entity.Event
import com.redloon.prm.data.db.entity.EventContact
import com.redloon.prm.data.db.entity.FollowUp
import com.redloon.prm.data.db.entity.Tag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ContactProfileUsabilityTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val ui = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var db: AppDatabase

    @Before fun setup() {
        hilt.inject()
        db.clearAllTables()
    }

    @Test fun sectionsRemainDiscoverableWithoutDisclosureEvenWhenEmpty() {
        runBlocking { db.contactDao().insert(Contact(displayName = "Empty Profile")) }
        openPerson()
        ui.onNodeWithTag("person_management").assertDoesNotExist()
        ui.onNodeWithTag("contact_detail_screen").performScrollToNode(hasTestTag("profile_links"))
        ui.onNodeWithText("Tags").assertIsDisplayed()
        ui.onNodeWithText("Relationships").assertIsDisplayed()
        ui.onNodeWithTag("relationship_add").assertIsDisplayed()
    }

    @Test fun denseProfileHasCountedBoundedListsAndDirectConnectionAccess() {
        runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Dense Profile"))
            repeat(15) { index ->
                db.contactFactDao().insert(ContactFact(contactId = person, text = "Fact $index"))
            }
            repeat(25) { index ->
                val event = db.eventDao().insert(Event(note = "Note $index"))
                db.eventDao().insertEventContact(EventContact(event, person))
            }
            repeat(8) { index ->
                db.followUpDao().insert(
                    FollowUp(
                        contactId = person,
                        text = "Voluntary follow-up $index",
                        completedAt = if (index < 2) 1_700_000_000_000L else null
                    )
                )
            }
        }
        openPerson()
        ui.onNodeWithText("Facts (15)").assertIsDisplayed().performClick()
        ui.waitUntilTagDisplayed("profile_facts_sheet", 10_000)
        ui.onNodeWithTag("profile_facts_list").performScrollToNode(hasText("Fact 0"))
        ui.onNodeWithText("Close").performClick()
        ui.onNodeWithText("Notes (25)").assertIsDisplayed().performClick()
        ui.waitUntilTagDisplayed("profile_notes_sheet", 10_000)
        ui.onNodeWithTag("profile_notes_list").performScrollToNode(hasText("Note 0"))
        ui.onNodeWithText("Close").performClick()
        ui.onNodeWithText("Follow-ups (8)").assertIsDisplayed()
        ui.onNodeWithText("Tags").assertIsDisplayed()
        ui.onNodeWithText("Relationships").assertIsDisplayed()
        ui.onNodeWithTag("relationship_add").assertIsDisplayed()
        ui.onNodeWithTag("person_management").assertDoesNotExist()
    }

    @Test fun assignedTagNavigatesWithoutRemovingAssociation() {
        val tag = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Tagged Profile"))
            val tag = db.tagDao().insert(Tag(name = "Garden"))
            db.profileLinksDao().addTag(ContactTag(person, tag))
            tag
        }
        openPerson()
        ui.onNodeWithTag(
            "contact_detail_screen"
        ).performScrollToNode(hasTestTag("profile_tag_$tag"))
        ui.onNodeWithTag("profile_tag_$tag").assertIsDisplayed().performClick()
        ui.waitUntilTagDisplayed("tag_detail_screen", 10_000)
        ui.onNodeWithText("Tagged Profile").assertIsDisplayed()
        assertTrue(runBlocking { db.profileLinksDao().allContactTags().any { it.tagId == tag } })
    }

    private fun openPerson() {
        ui.navigateToContactsTab()
        ui.waitUntilTagExists("contact_list_item", 10_000)
        ui.onAllNodesWithTag("contact_list_item")[0].performClick()
        ui.waitUntilTagDisplayed("contact_detail_screen", 10_000)
    }
}
