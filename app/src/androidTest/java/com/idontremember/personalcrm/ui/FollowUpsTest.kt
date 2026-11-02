package com.idontremember.personalcrm.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.followups.FollowUpRepository
import com.idontremember.personalcrm.data.settings.SettingsRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class FollowUpsTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var repository: FollowUpRepository
    @Inject lateinit var settings: SettingsRepository

    @Before fun setup() {
        hilt.inject()
        db.clearAllTables()
        runBlocking { settings.setFollowUpRemindersEnabled(false); settings.setFollowUpReminderPrompted(false) }
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("capture_draft", Context.MODE_PRIVATE).edit().clear().commit()
    }


    @Test fun followUpCanBeOpenedFromHomeEditedAndCompleted() {
        val id = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Morgan Followup"))
            repository.save(null, person, "Ask about hiking", null)
        }
        ui.waitUntilTagDisplayed("followup_$id", 10_000)
        ui.onNodeWithTag("followup_$id").performClick()
        ui.waitUntilTagDisplayed("followup_editor", 10_000)
        waitForLoadedEditor("Ask about hiking")
        ui.onNodeWithTag("followup_text").assertIsNotFocused().performClick().assertIsFocused()
        ui.onNodeWithTag("followup_text").performTextReplacement("Ask about the hiking photos")
        ui.onNodeWithTag("save_followup").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("followup_editor").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("Ask about the hiking photos").assertExists()
        ui.onNodeWithTag("followup_$id").performClick()
        ui.waitUntilTagDisplayed("followup_editor", 10_000)
        waitForLoadedEditor("Ask about the hiking photos")
        ui.onNodeWithTag("followup_text").performTextReplacement("Keep this edit when completing")
        ui.onNodeWithText("Mark done").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("followup_$id").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("View all").performScrollTo().performClick()
        ui.waitUntilTagDisplayed("followups_list", 10_000)
        ui.onNodeWithText("Completed").performClick()
        ui.onNodeWithText("Keep this edit when completing").assertExists()
    }
    @Test fun existingFollowUpCanBeCompletedWithoutOpeningKeyboard() {
        val id = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Completion Person"))
            repository.save(null, person, "Ask about pottery", null)
        }
        ui.waitUntilTagDisplayed("followup_$id", 10_000)
        // Open from a search with the keyboard already showing, as in ordinary recall.
        ui.onNodeWithTag("dashboard_search").performClick().performTextInput("pottery")
        ui.waitForKeyboard(visible = true)
        ui.waitUntilTagDisplayed("recall_followup_$id", 10_000)
        ui.onNodeWithTag("recall_followup_$id").performClick()
        ui.waitUntilTagDisplayed("followup_editor", 10_000)
        waitForLoadedEditor("Ask about pottery")
        ui.waitForKeyboard(visible = false)
        ui.onNodeWithTag("followup_text").assertIsNotFocused()
        ui.onNodeWithText("Mark done").assertIsDisplayed().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("followup_editor").fetchSemanticsNodes().isEmpty() }
        assertEquals("Ask about pottery", runBlocking { repository.get(id) }!!.text)
        org.junit.Assert.assertNotNull(runBlocking { repository.get(id) }!!.completedAt)
    }

    private fun waitForLoadedEditor(text: String) {
        ui.waitUntil(10_000) {
            ui.onAllNodes(hasTestTag("followup_text") and hasText(text)).fetchSemanticsNodes().isNotEmpty()
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
        ui.waitUntil(10_000) { ui.onAllNodes(hasTestTag("followup_text") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("followup_text").performTextInput("Remember the good news")
        ui.onNodeWithTag("followup_text").assertTextContains("Remember the good news")
    }

    @Test fun rotationDuringReminderChoiceCannotInsertAnotherFollowUp() {
        val person = runBlocking { db.contactDao().insert(Contact(displayName = "Rotation Person")) }
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("capture_draft", Context.MODE_PRIVATE).edit().putString("followup_new",
                JSONObject().put("text", "Ask about the trip").put("person", person).put("date", "2026-12-01").toString()).commit()
        ui.waitUntilTagDisplayed("add_event_fab", 10_000)
        ui.onNodeWithTag("add_event_fab").performClick()
        ui.waitUntilTagDisplayed("capture_follow_up_button", 10_000)
        ui.onNodeWithTag("capture_follow_up_button").performClick()
        ui.waitUntilTagDisplayed("followup_editor", 10_000)
        ui.onNodeWithTag("save_followup").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("A quiet reminder?").fetchSemanticsNodes().isNotEmpty() }
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("A quiet reminder?").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("save_followup").assertIsNotEnabled()
        ui.onNodeWithText("Not now").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("followup_editor").fetchSemanticsNodes().isEmpty() }
        assertEquals(1, runBlocking { db.followUpDao().getAllOnce().size })
    }

}
