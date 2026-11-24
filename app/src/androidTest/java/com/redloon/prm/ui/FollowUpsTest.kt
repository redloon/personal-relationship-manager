package com.redloon.prm.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.followups.FollowUpRepository
import com.redloon.prm.data.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class FollowUpsTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val ui = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var db: AppDatabase

    @Inject lateinit var repository: FollowUpRepository

    @Inject lateinit var settings: SettingsRepository

    @Before fun setup() {
        hilt.inject()
        db.clearAllTables()
        runBlocking {
            settings.setFollowUpRemindersEnabled(false)
            settings.setFollowUpReminderPrompted(false)
        }
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("capture_draft", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun followUpCanBeOpenedFromHomeEditedAndCompleted() {
        val id = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Morgan Followup"))
            repository.save(null, person, "Ask about hiking", null)
        }
        ui.waitUntilTagDisplayed("followups_shortcut", 10_000)
        ui.onNodeWithTag("followups_shortcut").performClick()
        ui.waitUntilTagDisplayed("followup_$id", 10_000)
        ui.onNodeWithTag("followup_$id").performClick()
        ui.waitUntilTagDisplayed("followup_reader", 10_000)
        ui.onNodeWithTag("followup_reader_edit").performClick()
        ui.waitUntilTagDisplayed("followup_editor", 10_000)
        waitForLoadedEditor("Ask about hiking")
        ui.onNodeWithTag("followup_text").assertIsNotFocused().performClick().assertIsFocused()
        ui.onNodeWithTag("followup_text").performTextReplacement("Ask about the hiking photos")
        ui.onNodeWithTag("save_followup").performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithTag("followup_editor").fetchSemanticsNodes().isEmpty()
        }
        ui.onNodeWithText("Ask about the hiking photos").assertExists()
        ui.onNodeWithTag("followup_$id").performClick()
        ui.waitUntilTagDisplayed("followup_reader", 10_000)
        ui.onNodeWithTag("followup_reader_edit").performClick()
        ui.waitUntilTagDisplayed("followup_editor", 10_000)
        waitForLoadedEditor("Ask about the hiking photos")
        ui.onNodeWithTag("followup_text").performTextReplacement("Keep this edit when completing")
        ui.onNodeWithText("Mark done").performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithTag("followup_$id").fetchSemanticsNodes().isEmpty()
        }
        ui.waitUntilTagDisplayed("followups_list", 10_000)
        ui.onNodeWithText("Completed").performClick()
        ui.onNodeWithText("Keep this edit when completing").assertExists()
    }

    @Test fun existingFollowUpCanBeCompletedWithoutOpeningKeyboard() {
        val id = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Completion Person"))
            repository.save(null, person, "Ask about pottery", null)
        }
        ui.waitUntilTagDisplayed("dashboard_search", 10_000)
        // Open from a search with the keyboard already showing, as in ordinary recall.
        ui.onNodeWithTag("dashboard_search").performClick().performTextInput("pottery")
        ui.waitForKeyboard(visible = true)
        ui.waitUntilTagDisplayed("recall_followup_$id", 10_000)
        ui.onNodeWithTag("recall_followup_$id").performClick()
        ui.waitUntilTagDisplayed("followup_reader", 10_000)
        ui.waitForKeyboard(visible = false)
        ui.onNodeWithTag("followup_editor").assertDoesNotExist()
        ui.onNodeWithTag("followup_reader_complete").assertIsDisplayed().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Reopen").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("close_followup_reader").performClick()
        assertEquals("Ask about pottery", runBlocking { repository.get(id) }!!.text)
        org.junit.Assert.assertNotNull(runBlocking { repository.get(id) }!!.completedAt)
    }

    private fun waitForLoadedEditor(text: String) {
        ui.waitUntil(10_000) {
            ui.onAllNodes(
                hasTestTag("followup_text") and hasText(text)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        // Loading is asynchronous; allow the focus effect and sheet animation to finish.
        ui.mainClock.advanceTimeBy(500)
        ui.waitForIdle()
    }

    @Test fun newFollowUpFocusesTextForImmediateCapture() {
        ui.waitUntilTagDisplayed("add_event_fab", 10_000)
        ui.onNodeWithTag("add_event_fab").performClick()
        ui.waitUntilTagDisplayed("capture_follow_up_button", 10_000)
        ui.onNodeWithTag("capture_follow_up_button").performClick()
        ui.waitUntilTagDisplayed("followup_editor", 10_000)
        ui.waitUntil(10_000) {
            ui.onAllNodes(
                hasTestTag("followup_text") and isFocused()
            ).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("followup_text").performTextInput("Remember the good news")
        ui.onNodeWithTag("followup_text").assertTextContains("Remember the good news")
    }

    @Test fun longFollowUpKeepsToolsVisibleAndCreatesFullNameWithoutLosingDraft() {
        val note = "Remember this next time. ".repeat(35)
        runBlocking {
            db.contactDao().insert(Contact(displayName = "Maya"))
            settings.setFollowUpReminderPrompted(true)
        }
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("capture_draft", Context.MODE_PRIVATE).edit().putString(
                "followup_new",
                JSONObject().put("text", note).put("date", "2026-12-01").toString()
            ).commit()
        ui.waitUntilTagDisplayed("add_event_fab", 10_000)
        ui.onNodeWithTag("add_event_fab").performClick()
        ui.waitUntilTagDisplayed("capture_follow_up_button", 10_000)
        ui.onNodeWithTag("capture_follow_up_button").performClick()
        ui.waitUntilTagDisplayed("followup_editor", 10_000)
        ui.waitForKeyboard(visible = true)
        ui.onNodeWithTag("followup_text").assertIsFocused().assertTextContains(note)
        ui.onNodeWithTag("save_followup").assertIsDisplayed()
        ui.onNodeWithTag("followup_choose_date").assertIsDisplayed()
        ui.onNodeWithTag("followup_choose_person").assertIsDisplayed().performClick()
        ui.waitUntilTagDisplayed("event_person_search", 10_000)
        ui.onNodeWithTag("event_person_search").performTextInput("Māya Chen")
        ui.onNodeWithTag("capture_create_person").performClick()
        ui.waitUntilTagDisplayed("person_name_input", 10_000)
        ui.onNodeWithTag("person_name_input").assertTextContains("Māya Chen")
        ui.onNodeWithTag("create_person_button").performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithTag("person_picker").fetchSemanticsNodes().isEmpty()
        }
        ui.onNodeWithTag("followup_text").assertTextContains(note)
        ui.onNodeWithTag("followup_choose_person").assertTextContains("Māya Chen")
        ui.onNodeWithTag("save_followup").performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithTag("followup_editor").fetchSemanticsNodes().isEmpty()
        }
        val saved = runBlocking { db.followUpDao().getAllOnce().single() }
        assertEquals(note.trim(), saved.text)
        assertEquals("2026-12-01", saved.dueDate)
        assertEquals(
            "Māya Chen",
            runBlocking {
                db.contactDao().getById(saved.contactId)
            }!!.displayName
        )
    }

    @Test fun rotationDuringReminderChoiceCannotInsertAnotherFollowUp() {
        val person =
            runBlocking { db.contactDao().insert(Contact(displayName = "Rotation Person")) }
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("capture_draft", Context.MODE_PRIVATE).edit().putString(
                "followup_new",
                JSONObject().put(
                    "text",
                    "Ask about the trip"
                ).put("person", person).put("date", "2026-12-01").toString()
            ).commit()
        ui.waitUntilTagDisplayed("add_event_fab", 10_000)
        ui.onNodeWithTag("add_event_fab").performClick()
        ui.waitUntilTagDisplayed("capture_follow_up_button", 10_000)
        ui.onNodeWithTag("capture_follow_up_button").performClick()
        ui.waitUntilTagDisplayed("followup_editor", 10_000)
        ui.onNodeWithTag("save_followup").performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("A quiet reminder?").fetchSemanticsNodes().isNotEmpty()
        }
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("A quiet reminder?").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("save_followup").assertIsNotEnabled()
        ui.onNodeWithText("Not now").performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithTag("followup_editor").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(1, runBlocking { db.followUpDao().getAllOnce().size })
    }
}
