package com.idontremember.personalcrm.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.idontremember.personalcrm.data.db.dao.EventDao
import com.idontremember.personalcrm.data.repository.EventRepository
import com.idontremember.personalcrm.ui.addevent.ShareCaptureActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ShareCaptureTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var events: EventRepository

    @Inject lateinit var eventDao: EventDao
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clean() {
        hilt.inject()
        context.getSharedPreferences("capture_draft", Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking { eventDao.deleteAll() }
    }

    private fun launchShare(text: String) = ActivityScenario.launch<ShareCaptureActivity>(
        Intent(context, ShareCaptureActivity::class.java).setAction(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    )

    @Test fun sharedTextIsPreservedAndOnlySavedOnRequest() {
        launchShare("Morgan likes this https://example.com/trail").use {
            compose.waitUntilTagDisplayed("note_text_field")
            compose.onNodeWithTag(
                "note_text_field"
            ).assertTextContains("Morgan likes this https://example.com/trail")
            org.junit.Assert.assertTrue(runBlocking { events.getAllEvents().first().isEmpty() })
            compose.onNodeWithTag("save_event_button").performClick()
            compose.waitUntil(10_000) {
                runBlocking {
                    events.getAllEvents().first().any {
                        it.note ==
                            "Morgan likes this https://example.com/trail"
                    }
                }
            }
        }
    }

    @Test fun sharingNeverSilentlyOverwritesAnUnfinishedDraft() {
        context.getSharedPreferences("capture_draft", Context.MODE_PRIVATE).edit()
            .putString(
                "draft",
                """{"note":"Existing unfinished note","contacts":[],"tags":[],"fact":false}"""
            ).commit()
        launchShare("Incoming shared text").use {
            compose.waitUntil(10_000) {
                compose.onAllNodes(
                    androidx.compose.ui.test.hasText("Resume draft")
                ).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Resume draft").performClick()
            compose.onNodeWithTag("note_text_field").assertTextContains("Existing unfinished note")
            org.junit.Assert.assertTrue(
                context.getSharedPreferences(
                    "capture_draft",
                    Context.MODE_PRIVATE
                ).getString("draft", "")!!.contains("Existing unfinished note")
            )
        }
    }

    @Test fun replacingDraftRequiresAnExplicitDiscardChoice() {
        context.getSharedPreferences("capture_draft", Context.MODE_PRIVATE).edit()
            .putString(
                "draft",
                """{"note":"Original draft","contacts":[],"tags":[],"fact":false}"""
            ).commit()
        launchShare("New shared text").use {
            compose.waitUntil(10_000) {
                compose.onAllNodes(
                    androidx.compose.ui.test.hasText("Discard draft and use shared text")
                ).fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertTrue(
                context.getSharedPreferences(
                    "capture_draft",
                    Context.MODE_PRIVATE
                ).getString("draft", "")!!.contains("Original draft")
            )
            compose.onNodeWithText("Discard draft and use shared text").performClick()
            compose.waitUntilTagDisplayed("note_text_field")
            compose.onNodeWithTag("note_text_field").assertTextContains("New shared text")
            compose.onNodeWithTag("save_event_button").performClick()
            compose.waitUntil(10_000) {
                runBlocking {
                    events.getAllEvents().first().any {
                        it.note ==
                            "New shared text"
                    }
                }
            }
        }
    }
}
