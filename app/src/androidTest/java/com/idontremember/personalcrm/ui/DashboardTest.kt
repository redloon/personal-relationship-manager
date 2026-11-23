package com.idontremember.personalcrm.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.db.entity.EventContact
import com.idontremember.personalcrm.data.db.entity.Relationship
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class DashboardTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val ui = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var db: AppDatabase

    @Before fun setup() {
        hilt.inject()
        db.clearAllTables()
    }

    @Test fun relationshipNavigatesBothWaysAndCanBeRemoved() {
        val (alice, bob) = runBlocking {
            val a = db.contactDao().insert(Contact(displayName = "Alice Relationship"))
            val b = db.contactDao().insert(Contact(displayName = "Bob Relationship"))
            a to b
        }
        ui.waitUntilTagDisplayed("dashboard_search", 10_000)
        ui.onNodeWithTag("dashboard_search").performTextInput("Alice Relationship")
        ui.waitUntilTagDisplayed("dashboard_person_$alice", 10_000)
        ui.onNodeWithTag("dashboard_person_$alice").performClick()
        ui.waitUntilTagDisplayed("contact_detail_screen", 10_000)
        ui.waitForKeyboard(visible = false)
        ui.onNodeWithTag("clear_person_search").performClick()
        ui.onNodeWithTag(
            "contact_detail_screen"
        ).performScrollToNode(hasTestTag("person_management"))
        ui.onNodeWithTag("person_management").assertIsDisplayed().performClick()
        ui.onNodeWithTag(
            "contact_detail_screen"
        ).performScrollToNode(hasTestTag("relationship_add"))
        ui.onNodeWithTag("relationship_add").assertIsDisplayed().performClick()
        ui.onNodeWithTag("add_event_sheet").assertDoesNotExist()
        ui.waitUntilTagDisplayed("relationship_label", 10_000)
        ui.onNodeWithTag("relationship_label").performTextInput("Friend")
        ui.onNodeWithText("Bob Relationship").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithTag("relationship_confirm").performClick()
        ui.waitUntilTagExists("relationship_person_$bob", 10_000)
        ui.onNodeWithTag(
            "contact_detail_screen"
        ).performScrollToNode(hasTestTag("relationship_person_$bob"))
        ui.onNodeWithTag("relationship_person_$bob").assertIsDisplayed().performClick()
        ui.onNodeWithTag(
            "contact_detail_screen"
        ).performScrollToNode(hasTestTag("person_management"))
        ui.onNodeWithTag("person_management").assertIsDisplayed().performClick()
        ui.waitUntilTagExists("relationship_person_$alice", 10_000)
        ui.onNodeWithTag(
            "contact_detail_screen"
        ).performScrollToNode(hasTestTag("relationship_remove_$alice"))
        ui.onNodeWithTag("relationship_remove_$alice").assertIsDisplayed().performClick()
        // Check persistence before the UI; this distinguishes a missed tap from a stale row.
        ui.waitUntil(10_000) { runBlocking { db.profileLinksDao().allRelationships().isEmpty() } }
        assertTrue(
            "Removing the relationship must delete its database link",
            runBlocking {
                db.profileLinksDao().allRelationships().isEmpty()
            }
        )
        ui.waitUntil(10_000) {
            ui.onAllNodesWithTag("relationship_person_$alice").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun recentAndSearchIncludeCorrectPeople() {
        runBlocking {
            val recent = db.contactDao().insert(Contact(displayName = "Recent Alice"))
            db.contactDao().insert(Contact(displayName = "Hidden Bob"))
            val event = db.eventDao().insert(Event(note = "Met", eventDate = 100))
            db.eventDao().insertEventContact(EventContact(event, recent))
        }
        ui.waitUntil(10000) {
            ui.onAllNodesWithText(
                "Recent Alice",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("Hidden Bob").assertDoesNotExist()
        ui.onNodeWithTag("clear_dashboard_search_button").assertDoesNotExist()
        ui.onNodeWithTag("dashboard_search").performTextInput("Bob")
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("Hidden Bob").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("clear_dashboard_search_button").assertIsDisplayed().performClick()
        ui.onNodeWithTag("dashboard_search").assert(
            SemanticsMatcher("Search query is empty") {
                it.config[SemanticsProperties.EditableText].text.isEmpty()
            }
        )
        ui.onNodeWithTag("clear_dashboard_search_button").assertDoesNotExist()
        ui.waitUntil(10000) {
            ui.onAllNodesWithText(
                "Recent Alice",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("Hidden Bob").assertDoesNotExist()
    }

    @Test fun forgottenFactOpensForReadingBeforeExplicitEditing() {
        val factId = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Morgan Recall"))
            db.contactFactDao().insert(
                ContactFact(contactId = person, text = "Enjoys climbing outdoors")
            )
        }
        ui.waitUntilTagDisplayed("dashboard_search", 10_000)
        ui.onNodeWithTag("dashboard_search").performTextInput("climbing")
        ui.waitUntilTagDisplayed("recall_fact_$factId", 10_000)
        ui.onNodeWithTag("recall_fact_$factId").performClick()
        ui.waitUntilTagDisplayed("fact_reader", 10_000)
        ui.onNodeWithTag("add_event_sheet").assertDoesNotExist()
        ui.onNodeWithTag("fact_reader_text").assertTextEquals("Enjoys climbing outdoors")
        ui.onNodeWithTag("fact_reader_edit").performClick()
        ui.waitUntilTagDisplayed("add_event_sheet", 10_000)
        ui.onNodeWithTag(
            "note_text_field"
        ).assertTextContains("Enjoys climbing outdoors", substring = true)
    }

    @Test fun factsAppearInRecentContextAndSearchSurvivesOpeningProfile() {
        val personId = runBlocking {
            val person = db.contactDao().insert(Contact(displayName = "Fact Only Person"))
            db.contactFactDao().insert(
                ContactFact(contactId = person, text = "Likes 100% chocolate")
            )
            person
        }
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("Likes 100% chocolate").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("dashboard_search").performTextInput("Fact Only")
        ui.waitUntilTagDisplayed("dashboard_person_$personId", 10_000)
        ui.onNodeWithTag("dashboard_person_$personId").performClick()
        ui.waitUntilTagDisplayed("contact_detail_screen", 10_000)
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntilTagDisplayed("dashboard_search", 10_000)
        ui.onNodeWithTag("dashboard_search").assertTextContains("Fact Only", substring = true)
    }

    @Test fun settingsOpensFromDashboard() {
        ui.waitUntilTagDisplayed("settings_button", 10_000)
        ui.onNodeWithTag("settings_button").performClick()
        ui.waitUntilTagDisplayed("settings_screen", 10_000)
    }

    @Test fun homeLimitsRecentPreviewAndKeepsFollowUpsDiscoverable() {
        runBlocking {
            repeat(20) {
                db.eventDao().insert(
                    Event(
                        note = "Recent note $it",
                        eventDate = it.toLong(),
                        createdAt = it.toLong()
                    )
                )
            }
        }
        ui.waitUntilTagDisplayed("followups_shortcut", 10_000)
        ui.onNodeWithTag("followups_shortcut").assertIsDisplayed()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("Recent note 19").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("recent_view_all").performScrollTo().performClick()
        ui.waitUntilTagDisplayed("events_list_screen", 10_000)
    }
}
