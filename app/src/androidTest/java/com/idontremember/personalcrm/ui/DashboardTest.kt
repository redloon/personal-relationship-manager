package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
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

@HiltAndroidTest
class DashboardTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var db: AppDatabase

    @Before fun setup() { hilt.inject(); db.clearAllTables() }

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
        ui.onNodeWithTag("relationship_add").performScrollTo().performClick()
        ui.onNodeWithTag("relationship_label").performTextInput("Friend")
        ui.onNodeWithText("Bob Relationship").performScrollTo().performClick()
        ui.onNodeWithTag("relationship_confirm").performClick()
        ui.waitUntilTagExists("relationship_person_$bob", 10_000)
        ui.onNodeWithTag("relationship_person_$bob").performScrollTo().performClick()
        ui.waitUntilTagExists("relationship_person_$alice", 10_000)
        ui.onNodeWithTag("relationship_remove_$alice").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("relationship_person_$alice").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun recentAndSearchIncludeCorrectPeople() {
        runBlocking {
            val recent = db.contactDao().insert(Contact(displayName = "Recent Alice"))
            db.contactDao().insert(Contact(displayName = "Hidden Bob"))
            val event = db.eventDao().insert(Event(note = "Met", eventDate = 100))
            db.eventDao().insertEventContact(EventContact(event, recent))
        }
        ui.waitUntil(10000) { ui.onAllNodesWithText("Recent Alice").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Hidden Bob").assertDoesNotExist()
        ui.onNodeWithTag("clear_dashboard_search_button").assertDoesNotExist()
        ui.onNodeWithTag("dashboard_search").performTextInput("Bob")
        ui.waitUntil(10000) { ui.onAllNodesWithText("Hidden Bob").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("clear_dashboard_search_button").assertIsDisplayed().performClick()
        ui.onNodeWithTag("dashboard_search").assert(SemanticsMatcher("Search query is empty") {
            it.config[SemanticsProperties.EditableText].text.isEmpty()
        })
        ui.onNodeWithTag("clear_dashboard_search_button").assertDoesNotExist()
        ui.waitUntil(10000) { ui.onAllNodesWithText("Recent Alice").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Hidden Bob").assertDoesNotExist()
    }

    @Test fun settingsOpensFromDashboard() {
        ui.waitUntilTagDisplayed("settings_button", 10_000)
        ui.onNodeWithTag("settings_button").performClick()
        ui.waitUntilTagDisplayed("settings_screen", 10_000)
    }
}
