package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.contacts.PeopleListScope
import com.idontremember.personalcrm.data.contacts.PeopleSort
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.db.entity.EventContact
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class PeopleFeaturesTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val ui = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var db: AppDatabase

    @Inject lateinit var repository: ContactRepository

    @Inject lateinit var settings: SettingsRepository

    @Before fun setup() {
        hilt.inject()
        db.clearAllTables()
        ui.activity.getSharedPreferences("capture_draft", 0).edit().clear().commit()
        resetSorts()
    }

    @After fun cleanup() {
        db.clearAllTables()
        resetSorts()
    }
    private fun resetSorts() = runBlocking {
        PeopleListScope.entries.forEach { settings.setPeopleSort(it, PeopleSort.RECENT) }
    }

    @Test fun recencyUsesPastEventsAndSortsAreRememberedIndependently() {
        val (never, recent, old) = runBlocking {
            val never = repository.createLocalContact("Alpha Fact")
            val recent = repository.createLocalContact("Beta Recent")
            val old = repository.createLocalContact("Zulu Past")
            db.contactDao().update(repository.getContactById(old)!!.copy(phoneNumber = "5550100"))
            db.contactFactDao().insert(ContactFact(contactId = never, text = "Latest fact"))
            val now = System.currentTimeMillis()
            for ((person, date) in listOf(
                recent to now - 3_600_000,
                old to now - 3 * 86_400_000,
                old to now + 86_400_000
            )) {
                val event = db.eventDao().insert(Event(note = "Context", eventDate = date))
                db.eventDao().insertEventContact(EventContact(event, person))
            }
            Triple(never, recent, old)
        }
        ui.navigateToContactsTab()
        ui.waitUntilTagDisplayed("person_row_$never")
        assertBefore(recent, old)
        assertBefore(old, never)
        ui.onAllNodesWithText("5550100").assertCountEquals(0)
        ui.onNode(
            hasTestTag("contact_list_item") and hasText("Alpha Fact")
        ).assertTextContains(
            ui.activity.getString(R.string.people_no_interactions),
            substring = true
        )
        chooseSort(PeopleSort.NAME_ASC)
        ui.waitUntil(5_000) {
            runBlocking {
                settings.peopleSort(PeopleListScope.ALL).first() ==
                    PeopleSort.NAME_ASC
            }
        }
        assertBefore(never, recent)
        ui.activityRule.scenario.recreate()
        ui.waitUntilTagDisplayed("person_row_$never")
        assertBefore(never, recent)
        ui.onNodeWithTag("people_menu").performClick()
        ui.onNodeWithTag("app_only_people_menu").performClick()
        ui.waitUntilTagDisplayed("app_only_people_screen")
        ui.onNodeWithText("Unlinked people").assertExists()
        assertBefore(recent, old)
        chooseSort(PeopleSort.NAME_DESC)
        ui.waitUntil(5_000) {
            runBlocking {
                settings.peopleSort(PeopleListScope.APP_ONLY).first() ==
                    PeopleSort.NAME_DESC
            }
        }
        assertBefore(old, recent)
        ui.onNodeWithTag("app_only_people_back").performClick()
        ui.waitUntilTagDisplayed("contacts_list_screen")
        assertBefore(never, recent)
        ui.onNodeWithTag("search_field").performTextInput("5550100")
        ui.waitUntilTagDisplayed("person_row_$old")
        ui.onNodeWithTag("person_row_$never").assertDoesNotExist()
    }

    @Test fun mixedSelectionsAreProtectedAndConfirmedDeletionKeepsEventNotes() {
        val (person, phone, event) = runBlocking {
            val person = repository.createLocalContact("Delete Typo")
            val phone = db.contactDao().insert(
                Contact(displayName = "Phone Protected", androidContactId = 42)
            )
            val event = db.eventDao().insert(Event(note = "A note that must survive"))
            db.eventDao().insertEventContact(EventContact(event, person))
            db.contactFactDao().insert(ContactFact(contactId = person, text = "Owned fact"))
            Triple(person, phone, event)
        }
        ui.navigateToContactsTab()
        ui.waitUntilTagDisplayed("person_row_$person")
        ui.onNodeWithTag("person_row_$person").performTouchInput { longClick() }
        ui.onNodeWithTag("person_row_$phone").performTouchInput { click() }
        ui.onNodeWithTag("people_selection_menu").performClick()
        ui.onNodeWithTag("people_delete_action").assertIsNotEnabled()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(
            android.view.KeyEvent.KEYCODE_BACK
        )
        ui.onNodeWithTag("people_selection_close").performClick()
        ui.onNodeWithTag("person_row_$person").performTouchInput { longClick() }
        requestDelete()
        ui.onNodeWithTag("cancel_people_delete").performClick()
        assertNotNull(runBlocking { repository.getContactById(person) })
        ui.onNodeWithTag("people_selection_count").assertExists()
        requestDelete()
        ui.onNodeWithTag("confirm_people_delete").performClick()
        ui.waitUntil(5_000) { runBlocking { repository.getContactById(person) == null } }
        ui.waitUntil(5_000) {
            ui.onAllNodesWithTag("person_row_$person").fetchSemanticsNodes().isEmpty()
        }
        assertNotNull(runBlocking { repository.getContactById(phone) })
        assertEquals(
            "A note that must survive",
            runBlocking {
                db.eventDao().getById(event)!!.note
            }
        )
        assertTrue(runBlocking { db.eventDao().getContactIdsForEvent(event).isEmpty() })
    }

    @Test fun eligiblePeopleCanBeDeletedFromHiddenAndUnlinkedManagement() {
        val (hidden, unlinked) = runBlocking {
            val hidden = repository.createLocalContact("Hidden Local")
            val unlinked = repository.createLocalContact("Unlinked Local")
            repository.setPeopleHidden(setOf(hidden), true)
            hidden to unlinked
        }
        ui.navigateToContactsTab()
        ui.onNodeWithTag("people_menu").performClick()
        ui.onNodeWithTag("hidden_people_menu").performClick()
        ui.waitUntilTagDisplayed("person_row_$hidden")
        ui.onNodeWithTag("person_row_$hidden").performTouchInput { longClick() }
        requestDelete()
        ui.onNodeWithTag("confirm_people_delete").performClick()
        ui.waitUntil(5_000) { runBlocking { repository.getContactById(hidden) == null } }
        ui.waitUntilTagDisplayed("hidden_people_back")
        ui.onNodeWithTag("hidden_people_back").performClick()
        ui.onNodeWithTag("people_menu").performClick()
        ui.onNodeWithTag("app_only_people_menu").performClick()
        ui.waitUntilTagDisplayed("person_row_$unlinked")
        ui.onNodeWithTag("person_row_$unlinked").performTouchInput { longClick() }
        requestDelete()
        ui.onNodeWithTag("confirm_people_delete").performClick()
        ui.waitUntil(5_000) { runBlocking { repository.getContactById(unlinked) == null } }
    }

    private fun chooseSort(sort: PeopleSort) {
        ui.onNodeWithTag("people_menu").performClick()
        ui.onNodeWithTag("people_sort_menu").performClick()
        ui.waitUntilTagDisplayed("people_sort_dialog")
        ui.onNodeWithTag("people_sort_${sort.name}").performScrollTo().performClick()
    }
    private fun requestDelete() {
        ui.onNodeWithTag("people_selection_menu").performClick()
        ui.onNodeWithTag("people_delete_action").assertIsEnabled().performClick()
        ui.waitUntilTagDisplayed("people_delete_dialog")
    }
    private fun assertBefore(first: Long, second: Long) {
        ui.waitUntil(5_000) {
            ui.onNodeWithTag("person_row_$first").fetchSemanticsNode().positionInRoot.y <
                ui.onNodeWithTag("person_row_$second").fetchSemanticsNode().positionInRoot.y
        }
    }
}
