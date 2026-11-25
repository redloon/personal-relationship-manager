package com.redloon.prm.ui

import android.content.ContentResolver
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.redloon.prm.data.contacts.InteractionRepository
import com.redloon.prm.data.db.dao.ContactDao
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.repository.ContactRepository
import com.redloon.prm.data.settings.SettingsRepository
import com.redloon.prm.testdata.PopulationFixtures
import com.redloon.prm.testdata.RandomContactGenerator
import com.redloon.prm.ui.contacts.ContactsListViewModel
import com.redloon.prm.ui.contacts.PossibleDuplicatesScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the ContactsListScreen.
 * Verifies the contacts list renders correctly and the sync button works.
 */
@HiltAndroidTest
class ContactsListScreenTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var contactDao: ContactDao

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var repository: ContactRepository

    @Inject lateinit var interactions: InteractionRepository

    private lateinit var resolver: ContentResolver

    @Before
    fun setup() {
        hiltRule.inject()
        resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        RandomContactGenerator.clearContactsProvider(resolver)
        runBlocking {
            // Start clean: the test DB singleton is shared across tests in this run
            // (e.g. AppDatabaseTest inserts contacts) — clear before asserting on
            // the empty state.
            contactDao.getAll().first().forEach { contactDao.delete(it) }
            settings.setContactImportOnboardingDismissed(false)
            settings.setAutomaticContactSyncEnabled(false)
            settings.setLastSuccessfulContactSyncAt(0)
            settings.clearDismissedReconnectPairs()
        }
        composeTestRule.navigateToContactsTab()
    }

    @After
    fun cleanupContactSyncSettings() = runBlocking {
        // Other UI classes exercise an established People screen; this class alone owns first use.
        settings.setContactImportOnboardingDismissed(true)
        settings.setAutomaticContactSyncEnabled(false)
        settings.setLastSuccessfulContactSyncAt(0)
        RandomContactGenerator.clearContactsProvider(resolver)
    }

    @Test
    fun contactsScreen_showsTitle() {
        // testTag instead of onNodeWithText("Contacts") — that label appears
        // twice (header + bottom-nav tab) and the matcher is ambiguous.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithTag("contacts_list_screen").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun contactsScreen_showsEmptyStateWhenNoContacts() {
        // P2: the shared EmptyState composable renders this — tag + localized text.
        composeTestRule.waitUntilTagDisplayed("contacts_empty_state")
        composeTestRule.onNodeWithTag("contacts_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("No people yet.", substring = true).assertExists()
    }

    @Test
    fun contactsScreen_searchFieldExists() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithTag("search_field").assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun firstVisitShowsImportExplanationAndDismissalPersists() {
        composeTestRule.waitUntilTagDisplayed("contact_import_onboarding")
        composeTestRule.onNodeWithText(
            "Importing adds all current Android contacts.",
            substring = true
        )
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("contact_import_not_now").performClick()
        composeTestRule.waitUntil(5_000) {
            runBlocking { settings.contactImportOnboardingDismissed.first() }
        }
        composeTestRule.onNodeWithTag("contact_import_onboarding").assertDoesNotExist()

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitUntilTagDisplayed("contacts_list_screen")
        composeTestRule.onNodeWithTag("contact_import_onboarding").assertDoesNotExist()
    }

    @Test
    fun overflowLabelsImportBeforeOptInAndRefreshAfterward() {
        composeTestRule.onNodeWithTag("people_menu").performClick()
        composeTestRule.onNodeWithTag("contacts_sync_menu")
            .assertIsDisplayed().assertTextContains("Import Android contacts")

        runBlocking { settings.setAutomaticContactSyncEnabled(true) }
        composeTestRule.waitUntil(5_000) {
            runBlocking { settings.automaticContactSyncEnabled.first() }
        }
        composeTestRule.onNodeWithTag("contacts_sync_menu")
            .assertIsDisplayed().assertTextContains("Refresh Android contacts")
    }

    @Test
    fun contactsScreen_searchFieldAcceptsInput() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithTag("search_field").assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        composeTestRule.onNodeWithTag("search_field").performTextInput("Alice")
        composeTestRule.waitForIdle()
        // After typing, should show "No contacts match your search" or results
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            try {
                composeTestRule.onNodeWithText("No people match your search.").assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun clearSearchRestoresContacts() {
        runBlocking {
            PopulationFixtures.seedBackgroundPeople(contactDao)
            contactDao.insert(
                com.redloon.prm.data.db.entity.Contact(displayName = "Alice")
            )
        }
        composeTestRule.waitUntilTagDisplayed("contacts_list")
        composeTestRule.onNodeWithTag("search_field").performTextInput("nobody")
        composeTestRule.waitUntilTagDisplayed("contacts_empty_state")
        composeTestRule.onNodeWithTag("clear_contacts_search_button").performClick()
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_contacts_search_button").assertDoesNotExist()
    }

    @Test
    fun possibleDuplicateCanBeDismissedFromTheDurablePeopleMenu() {
        runBlocking {
            contactDao.insert(Contact(displayName = "Maya Chen", phoneNumber = "4155550123"))
            contactDao.insert(
                Contact(
                    androidContactId = 42,
                    displayName = "Maya Alvarez",
                    phoneNumber = "415-555-0123"
                )
            )
        }
        composeTestRule.onNodeWithTag("people_menu").performClick()
        composeTestRule.waitUntilTagDisplayed("review_duplicates_menu")
        composeTestRule.onNodeWithTag("review_duplicates_menu").performClick()
        composeTestRule.onNodeWithTag("possible_duplicates_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Saved profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Android contact").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dismiss_duplicate").performClick()
        composeTestRule.waitUntil(5_000) {
            runBlocking { settings.dismissedReconnectPairs.first().size == 1 }
        }
        composeTestRule.waitUntilTagDisplayed("duplicates_complete")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithTag("people_menu").performClick()
        composeTestRule.onNodeWithTag("review_duplicates_menu").assertDoesNotExist()
    }

    @Test
    fun possibleDuplicateReconnectKeepsTheSavedProfile() {
        val saved = runBlocking {
            val keeper = contactDao.insert(Contact(displayName = "Maya Chen"))
            contactDao.insert(Contact(androidContactId = 42, displayName = "Maya Chen"))
            keeper
        }
        composeTestRule.onNodeWithTag("people_menu").performClick()
        composeTestRule.waitUntilTagDisplayed("review_duplicates_menu")
        composeTestRule.onNodeWithTag("review_duplicates_menu").performClick()
        composeTestRule.onNodeWithTag("reconnect_profiles").performClick()
        composeTestRule.waitUntil(5_000) {
            runBlocking {
                val profiles = contactDao.getAllIncludingHiddenOnce()
                profiles.size == 1 && profiles.single().id == saved &&
                    profiles.single().androidContactId == 42L
            }
        }
        composeTestRule.waitUntilTagDisplayed("duplicates_complete")
    }

    @Test
    fun syncSnackbarReviewActionOpensPossibleDuplicate() {
        RandomContactGenerator.seedContactsProvider(
            resolver,
            listOf(
                RandomContactGenerator.SystemContact(
                    1,
                    "Maya Alvarez",
                    "415-555-0123",
                    null
                )
            )
        )
        runBlocking {
            contactDao.insert(Contact(displayName = "Maya Chen", phoneNumber = "4155550123"))
            settings.setContactImportOnboardingDismissed()
            settings.setAutomaticContactSyncEnabled(true)
        }
        composeTestRule.onNodeWithTag("people_menu").performClick()
        composeTestRule.onNodeWithTag("contacts_sync_menu").performClick()
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText(
                "possible duplicate needs review",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Review").performClick()
        composeTestRule.onNodeWithTag("possible_duplicates_screen").assertIsDisplayed()
    }

    @Test fun reviewListScrollsResolvesPairsAndSurvivesBackAndRecreation() {
        val pairs = runBlocking {
            PopulationFixtures.seedBackgroundPeople(contactDao)
            (1..8).map { index ->
                val saved = contactDao.insert(Contact(displayName = "Review $index"))
                val android = contactDao.insert(
                    Contact(androidContactId = 100L + index, displayName = "Review $index")
                )
                saved to android
            }
        }
        composeTestRule.onNodeWithTag("people_menu").performClick()
        composeTestRule.waitUntilTagDisplayed("review_duplicates_menu")
        composeTestRule.onNodeWithTag("review_duplicates_menu").performClick()
        fun row(index: Int) = "duplicate_${pairs[index].first}_${pairs[index].second}"
        composeTestRule.onNodeWithTag(row(0)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("possible_duplicates_list")
            .performScrollToNode(hasTestTag(row(1)))
        composeTestRule.onNodeWithTag(row(1)).assertIsDisplayed()
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitUntilTagDisplayed("possible_duplicates_screen")
        composeTestRule.onNodeWithTag("possible_duplicates_list")
            .performScrollToNode(hasTestTag(row(7)))
        composeTestRule.onNode(
            hasTestTag("dismiss_duplicate") and hasAnyAncestor(hasTestTag(row(7)))
        )
            .performClick()
        composeTestRule.waitUntil(5_000) {
            runBlocking { settings.dismissedReconnectPairs.first().size == 1 }
        }
        composeTestRule.onNodeWithTag(row(7)).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntilTagDisplayed("contacts_list_screen")
        composeTestRule.onNodeWithTag("people_menu").performClick()
        composeTestRule.onNodeWithTag("review_duplicates_menu").performClick()
        composeTestRule.onNodeWithTag("possible_duplicates_list")
            .performScrollToNode(hasTestTag(row(0)))
        composeTestRule.onNode(
            hasTestTag("reconnect_profiles") and hasAnyAncestor(hasTestTag(row(0)))
        )
            .performClick()
        composeTestRule.waitUntil(5_000) {
            runBlocking { contactDao.getById(pairs[0].first)?.androidContactId == 101L }
        }
        composeTestRule.onNodeWithTag(row(0)).assertDoesNotExist()
    }

    @Test fun staleCandidateFailureKeepsReviewOpenAndDoesNotMergeProfiles() {
        val candidate = runBlocking {
            contactDao.insert(Contact(displayName = "Stale Person"))
            contactDao.insert(Contact(androidContactId = 42, displayName = "Stale Person"))
            repository.observeReconnectCandidates().first().single()
        }
        val store = ViewModelStore()
        lateinit var model: ContactsListViewModel
        composeTestRule.activityRule.scenario.onActivity { activity ->
            model = ContactsListViewModel(repository, settings, interactions, SavedStateHandle())
            store.put("review", model)
            activity.setContent {
                MaterialTheme { PossibleDuplicatesScreen({}, viewModel = model) }
            }
        }
        try {
            composeTestRule.waitUntilTagDisplayed("reconnect_profiles")
            runBlocking {
                contactDao.update(candidate.androidProfile.copy(displayName = "Changed Person"))
            }
            composeTestRule.activityRule.scenario.onActivity { model.reconnect(candidate) }
            composeTestRule.waitUntilTagDisplayed("duplicates_complete")
            composeTestRule.onNodeWithText(
                "These profiles changed or couldn’t be reconnected. Review them again."
            )
                .assertIsDisplayed()
            org.junit.Assert.assertEquals(
                2,
                runBlocking {
                    contactDao.getAllIncludingHiddenOnce().size
                }
            )
        } finally {
            composeTestRule.activityRule.scenario.onActivity { store.clear() }
        }
    }
}
