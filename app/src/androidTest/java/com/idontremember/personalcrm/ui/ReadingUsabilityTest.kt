package com.idontremember.personalcrm.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.db.entity.EventContact
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ReadingUsabilityTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val ui = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var db: AppDatabase

    @Before fun setup() {
        hilt.inject()
        db.clearAllTables()
        ui.activity.getSharedPreferences(
            "capture_draft",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test fun factRecallOpensReaderWithoutDraftAndCanNavigateToPerson() {
        val (person, fact) = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Mira Reader"))
            person to
                db.contactFactDao().insert(
                    ContactFact(contactId = person, text = "Enjoys pottery and sculpture")
                )
        }
        ui.waitUntilTagDisplayed("dashboard_search", 10_000)
        ui.onNodeWithTag("dashboard_search").performTextInput("pottery")
        ui.waitForKeyboard(true)
        ui.waitUntilTagDisplayed("recall_fact_$fact", 10_000)
        ui.onNodeWithTag("recall_fact_$fact").performClick()
        ui.waitUntilTagDisplayed("fact_reader", 10_000)
        ui.waitForKeyboard(false)
        ui.onNodeWithTag("fact_reader_text").assertTextEquals("Enjoys pottery and sculpture")
        ui.onNodeWithTag("add_event_sheet").assertDoesNotExist()
        assertFalse(
            ui.activity.getSharedPreferences(
                "capture_draft",
                Context.MODE_PRIVATE
            ).contains("fact_$fact")
        )
        ui.onNodeWithTag("fact_reader_person").performClick()
        ui.waitUntilTagDisplayed("contact_detail_screen", 10_000)
        ui.onNodeWithText("Mira Reader").assertExists()
        assertNotNull(runBlocking { db.contactDao().getById(person) })
    }

    @Test fun profileFactIsFullWidthAndDeleteCanBeUndoneFromReader() {
        val fact = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Profile Reader"))
            db.contactFactDao().insert(ContactFact(contactId = person, text = "Short fact"))
        }
        openPerson()
        ui.onNodeWithTag("fact_row_$fact").assertIsDisplayed()
        ui.onNodeWithTag("edit_fact_$fact").assertDoesNotExist()
        ui.onNodeWithTag("delete_fact_$fact").assertDoesNotExist()
        ui.onNodeWithText("Read more").assertDoesNotExist()
        ui.onNodeWithTag("fact_row_$fact").performClick()
        ui.waitUntilTagDisplayed("fact_reader", 10_000)
        ui.onNodeWithTag("delete_fact_$fact").performClick()
        ui.waitUntil(10_000) { runBlocking { db.contactFactDao().getById(fact) } == null }
        ui.onNodeWithText("Undo").performClick()
        ui.waitUntil(10_000) { runBlocking { db.contactFactDao().getById(fact) } != null }
        ui.onNodeWithTag("fact_reader_text").assertTextEquals("Short fact")
    }

    @Test fun longPreviewSignalsMoreAndOpensCompleteText() {
        val text =
            "A remembered detail about hiking. ".repeat(35) + "Final hidden detail about surgery."
        val event = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Preview Reader"))
            val event = db.eventDao().insert(Event(note = text))
            db.eventDao().insertEventContact(EventContact(event, person))
            event
        }
        openPerson()
        ui.onNodeWithTag("event_preview_$event").performScrollTo()
        ui.onNodeWithText("Read more").performScrollTo().assertIsDisplayed().performClick()
        ui.waitUntilTagDisplayed("event_detail_screen", 10_000)
        ui.onNodeWithText(text).assertExists()
    }

    @Test fun editPersonIsAvailableInHeaderWithoutExpandingManagement() {
        val person = runBlocking { db.contactDao().insert(Contact(displayName = "Before Rename")) }
        openPerson()
        ui.onNodeWithTag("edit_person_button").assertIsDisplayed().performClick()
        ui.waitUntilTagDisplayed("rename_person_name", 10_000)
        ui.onNodeWithTag("rename_person_name").performTextReplacement("After Rename")
        ui.onNodeWithTag("link_android_contact").assertIsDisplayed()
        ui.onNodeWithTag("save_person_name").performClick()
        ui.waitUntil(10_000) {
            runBlocking { db.contactDao().getById(person) }?.displayName ==
                "After Rename"
        }
    }

    private fun openPerson() {
        ui.navigateToContactsTab()
        ui.waitUntilTagExists("contact_list_item", 10_000)
        ui.onAllNodesWithTag("contact_list_item")[0].performClick()
        ui.waitUntilTagDisplayed("contact_detail_screen", 10_000)
    }
}
