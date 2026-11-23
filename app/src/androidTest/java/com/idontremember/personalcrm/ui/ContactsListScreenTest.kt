package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.idontremember.personalcrm.data.db.dao.ContactDao
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            // Start clean: the test DB singleton is shared across tests in this run
            // (e.g. AppDatabaseTest inserts contacts) — clear before asserting on
            // the empty state.
            contactDao.getAll().first().forEach { contactDao.delete(it) }
        }
        composeTestRule.navigateToContactsTab()
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
    fun contactsScreen_syncButtonExists() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithTag("sync_button").assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun contactsScreen_syncButtonIsClickable() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeTestRule.onNodeWithTag("sync_button").assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        composeTestRule.onNodeWithTag("sync_button").performClick()
        // Should not crash — sync may fail if no permission, but the button should work
        composeTestRule.waitForIdle()
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
            contactDao.insert(
                com.idontremember.personalcrm.data.db.entity.Contact(displayName = "Alice")
            )
        }
        composeTestRule.waitUntilTagDisplayed("contact_list_item")
        composeTestRule.onNodeWithTag("search_field").performTextInput("nobody")
        composeTestRule.waitUntilTagDisplayed("contacts_empty_state")
        composeTestRule.onNodeWithTag("clear_contacts_search_button").performClick()
        composeTestRule.waitUntilTagDisplayed("contact_list_item")
        composeTestRule.onNodeWithTag("clear_contacts_search_button").assertDoesNotExist()
    }
}
