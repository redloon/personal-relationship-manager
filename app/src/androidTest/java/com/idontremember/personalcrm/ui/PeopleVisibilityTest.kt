package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.Contact
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class PeopleVisibilityTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var db: AppDatabase
    private var maya = 0L
    private var sam = 0L

    @After fun cleanup() {
        db.clearAllTables()
    }

    @Before fun setup() {
        hilt.inject()
        runBlocking {
            db.clearAllTables()
            maya = db.contactDao().insert(Contact(displayName = "Maya Chen", updatedAt = 20))
            sam =
                db.contactDao().insert(Contact(displayName = "Sam Lee", email = "sam@example.com"))
        }
        compose.navigateToContactsTab()
        compose.waitUntilTagDisplayed("person_row_$maya")
    }

    @Test fun longPressBulkHideAndUnhidePreserveTheSameProfiles() {
        val before = runBlocking { db.contactDao().getAllIncludingHiddenOnce() }
        compose.onNodeWithTag("person_row_$maya").performTouchInput { longClick() }
        compose.onNodeWithTag("person_row_$sam").performTouchInput { click() }
        compose.onNodeWithTag("people_selection_count").assertTextEquals("2 selected")
        compose.onNodeWithTag("people_visibility_action").performClick()
        compose.waitUntilTagDisplayed("contacts_empty_state")
        assertTrue(runBlocking { db.contactDao().getAllIncludingHiddenOnce().all { it.isHidden } })
        compose.onNodeWithTag("people_menu").performClick()
        compose.onNodeWithTag("hidden_people_menu").performClick()
        compose.waitUntilTagDisplayed("hidden_people_screen")
        compose.waitUntilTagDisplayed("person_row_$maya")
        compose.onNodeWithTag("person_row_$maya").performTouchInput { longClick() }
        compose.onNodeWithTag("people_selection_menu").performClick()
        compose.onNodeWithTag("people_select_all").performClick()
        compose.onNodeWithTag("people_visibility_action").performClick()
        compose.waitUntilTagDisplayed("contacts_empty_state")
        assertEquals(before, runBlocking { db.contactDao().getAllIncludingHiddenOnce() })
    }

    @Test fun searchSelectionPersistsAndBackExitsWithoutChangingData() {
        compose.onNodeWithTag("people_menu").performClick()
        compose.onNodeWithTag("people_select_menu").performClick()
        compose.onNodeWithTag("person_row_$maya").performTouchInput { click() }
        compose.onNodeWithTag("search_field").performTextInput("sam@example.com")
        compose.waitUntilTagDisplayed("person_row_$sam")
        compose.onNodeWithTag("person_row_$maya").assertDoesNotExist()
        compose.onNodeWithTag("people_selection_menu").performClick()
        compose.onNodeWithTag("people_select_all").performClick()
        compose.onNodeWithTag("people_selection_count").assertTextEquals("2 selected")
        compose.activityRule.scenario.recreate()
        compose.waitUntilTagDisplayed("people_selection_count")
        compose.onNodeWithTag("people_selection_count").assertTextEquals("2 selected")
        compose.onNodeWithTag("search_field").assertTextContains("sam@example.com")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilTagDisplayed("people_menu")
        assertTrue(runBlocking { db.contactDao().getAllIncludingHiddenOnce().none { it.isHidden } })
    }

    @Test fun failedHideKeepsSelectionAndRetryChangesOnlyVisibility() {
        val sql = db.openHelper.writableDatabase
        sql.execSQL(
            "CREATE TRIGGER fail_visibility BEFORE UPDATE OF is_hidden ON contacts BEGIN SELECT RAISE(ABORT, 'Test failure'); END"
        )
        try {
            compose.onNodeWithTag("person_row_$maya").performTouchInput { longClick() }
            compose.onNodeWithTag("people_visibility_action").performClick()
            compose.waitUntilTagDisplayed("people_visibility_retry")
            compose.onNodeWithTag("people_selection_count").assertTextEquals("1 selected")
            assertFalse(runBlocking { db.contactDao().getById(maya)!!.isHidden })
        } finally {
            sql.execSQL("DROP TRIGGER fail_visibility")
        }
        compose.onNodeWithTag("people_visibility_retry").performClick()
        compose.waitUntil(5_000) { runBlocking { db.contactDao().getById(maya)!!.isHidden } }
        assertFalse(runBlocking { db.contactDao().getById(sam)!!.isHidden })
    }

    @Test fun autocompleteExcludesHiddenPeopleAndKeepsAlreadySelectedNames() {
        runBlocking { db.contactDao().setHidden(listOf(maya), true) }
        compose.onNodeWithTag("nav_tab_home").performClick()
        compose.onNodeWithTag("add_event_fab").performClick()
        compose.waitUntilTagDisplayed("note_text_field")
        compose.onNodeWithTag("note_text_field").performTextInput("Met @Maya")
        compose.onNodeWithText("Maya Chen").assertDoesNotExist()
        compose.onNodeWithTag("note_text_field").performTextReplacement("Met @Sam")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Sam Lee").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Sam Lee").performClick()
        runBlocking { db.contactDao().setHidden(listOf(sam), true) }
        compose.onNodeWithText("@Sam Lee").assertIsDisplayed()
        compose.onNodeWithTag("save_event_button").performClick()
        compose.waitUntil(5_000) { runBlocking { db.eventDao().getAllOnce().isNotEmpty() } }
        val event = runBlocking { db.eventDao().getAllOnce().single() }
        assertEquals(listOf(sam), runBlocking { db.eventDao().getContactIdsForEvent(event.id) })
    }

    @Test fun hideFeedbackStaysAboveTheKeyboard() {
        compose.onNodeWithTag("search_field").performTextInput("Maya")
        compose.waitForKeyboard(true)
        compose.onNodeWithTag("person_row_$maya").performTouchInput { longClick() }
        compose.onNodeWithTag("people_visibility_action").performClick()
        val undo = compose.onNodeWithText("Undo").assertIsDisplayed().fetchSemanticsNode()
        var keyboardTop = 0
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val ime = androidx.core.view.ViewCompat.getRootWindowInsets(decor)!!
                .getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            assertTrue("The regression must run with the keyboard open", ime > 0)
            keyboardTop = decor.height - ime
        }
        assertTrue(
            "Undo must be above the keyboard: ${undo.boundsInWindow}, keyboard top $keyboardTop",
            undo.boundsInWindow.bottom <= keyboardTop + 1
        )
    }

    @Test fun undoAndPickersRespectVisibility() {
        compose.onNodeWithTag("person_row_$maya").performTouchInput { longClick() }
        compose.onNodeWithTag("people_visibility_action").performClick()
        compose.waitUntil(5_000) { runBlocking { db.contactDao().getById(maya)!!.isHidden } }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntilTagDisplayed("person_row_$maya")
        compose.onNodeWithTag("person_row_$maya").performTouchInput { longClick() }
        compose.onNodeWithTag("people_visibility_action").performClick()
        compose.waitUntil(5_000) { runBlocking { db.contactDao().getById(maya)!!.isHidden } }
        compose.onNodeWithTag("add_person_button").performClick()
        compose.waitUntilTagDisplayed("person_name_input")
        compose.onNodeWithTag("person_name_input").performTextInput("Maya Chen")
        compose.onNodeWithTag("event_person_option_$maya").assertDoesNotExist()
    }
}
