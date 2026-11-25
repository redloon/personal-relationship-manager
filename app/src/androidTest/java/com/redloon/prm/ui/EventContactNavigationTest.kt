package com.redloon.prm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.redloon.prm.data.db.dao.ContactDao
import com.redloon.prm.data.db.dao.EventDao
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.repository.EventRepository
import com.redloon.prm.testdata.PopulationFixtures
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class EventContactNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var contactDao: ContactDao

    @Inject lateinit var eventDao: EventDao

    @Inject lateinit var eventRepository: EventRepository

    private var contactId = 0L

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            eventDao.deleteAll()
            contactDao.deleteAll()
            PopulationFixtures.seedBackgroundPeople(contactDao)
            contactId = contactDao.insert(Contact(displayName = "Alice"))
            eventRepository.createEvent("Met Alice", listOf(contactId), emptyList())
        }
    }

    @Test
    fun eventListPersonOpensContactAndBackReturnsToList() {
        composeTestRule.navigateToHistory()
        composeTestRule.waitUntilTagExists("event_contact_chip_$contactId")
        composeTestRule.onNodeWithTag("event_contact_chip_$contactId").performClick()
        composeTestRule.waitUntilTagDisplayed("contact_detail_screen")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntilTagDisplayed("events_list_screen")
    }

    @Test
    fun eventDetailPersonOpensContactAndBackReturnsToEvent() {
        composeTestRule.navigateToHistory()
        composeTestRule.waitUntilTagExists("event_contact_chip_$contactId")
        composeTestRule.onNodeWithText("Met Alice").performClick()
        composeTestRule.waitUntilTagDisplayed("event_detail_screen")
        composeTestRule.onNodeWithTag("event_contact_chip_$contactId").performClick()
        composeTestRule.waitUntilTagDisplayed("contact_detail_screen")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntilTagDisplayed("event_detail_screen")
    }

    @Test
    fun renamingContactKeepsOldNoteAndLink() {
        runBlocking {
            val contact = contactDao.getById(contactId)!!
            contactDao.update(contact.copy(displayName = "Alice New"))
        }
        composeTestRule.navigateToHistory()
        composeTestRule.waitUntilTagExists("event_contact_chip_$contactId")
        composeTestRule.onNodeWithText("Met Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("@Alice New").assertIsDisplayed()
        composeTestRule.onNodeWithTag("event_contact_chip_$contactId").performClick()
        composeTestRule.waitUntilTagDisplayed("contact_detail_screen")
    }
}
