package com.idontremember.personalcrm.ui

import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.dao.ContactFactDao
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.repository.EventRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * P2 accessibility smoke tests:
 *
 * 1. TalkBack reading order on Contact Detail, verified by traversing the merged
 *    semantics tree top-to-bottom (the order TalkBack reads a vertical layout in).
 * 2. Empty states render through the shared `EmptyState` composable.
 * 3. Dynamic type: at max font scale (1.3x) key content stays visible and icon
 *    touch targets stay at least 48dp.
 *
 * NOTE: the in-memory test database is a process-wide Hilt singleton shared by
 * all tests in one instrumentation run, so setup clears the tables asserted on.
 */
@HiltAndroidTest
class AccessibilitySmokeTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var contactDao: ContactDao

    @Inject
    lateinit var contactFactDao: ContactFactDao

    @Inject
    lateinit var eventRepository: EventRepository

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            // Start clean: the test DB singleton is shared across tests in this run.
            contactDao.getAll().first().forEach { contactDao.delete(it) } // cascades facts
            eventRepository.getAllEvents().first().forEach { eventRepository.deleteEvent(it) }
        }
    }

    @After
    fun tearDown() {
        // Never leak a non-default font scale into the rest of the run.
        setFontScale(1.0f)
    }

    @Test
    fun contactDetail_readsContentInTalkBackOrder() {
        val contactId = seedContact()
        runBlocking {
            contactFactDao.insert(ContactFact(contactId = contactId, text = FACT_TEXT))
            eventRepository.createEvent(
                note = NOTE_TEXT,
                contactIds = listOf(contactId),
                tagNames = emptyList(),
                eventDate = 1_700_000_000_000L
            )
        }

        openFirstContactDetail()

        // TalkBack order for a vertical layout = texts sorted top-to-bottom.
        val readingOrder = readingOrder()
        val texts = readingOrder.map { it.first }
        // NAME shows twice (top-bar title + header card); the header copy must be
        // the one just before the phone/email lines of the merged header node.
        val iName = texts.lastIndexOf(NAME)
        val iPhone = texts.indexOf(PHONE)
        val iEmail = texts.indexOf(EMAIL)
        val iFacts = texts.indexOf("Facts")
        val iFact = texts.indexOf(FACT_TEXT)
        val iEvents = texts.indexOf("Events")
        val iNote = texts.indexOf(NOTE_TEXT)

        assertTrue(
            "Expected all content in the semantics tree, got: $readingOrder",
            listOf(iName, iPhone, iEmail, iFacts, iFact, iEvents, iNote).all { it >= 0 }
        )
        assertTrue(
            "TalkBack order should be name → phone → email → Facts → fact → Events → note, got: $readingOrder",
            iName < iPhone && iPhone < iEmail && iEmail < iFacts &&
                iFacts < iFact && iFact < iEvents && iEvents < iNote
        )
    }

    @Test
    fun contactDetail_showsSectionEmptyStates() {
        val contactId = seedContact() // no facts, no events
        openFirstContactDetail()

        composeTestRule.waitUntilTagDisplayed("facts_empty_state")
        composeTestRule.onNodeWithTag("facts_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("contact_events_empty_state")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun maxFontScale_keyContentVisibleAndTouchTargetsAdequate() {
        setFontScale(1.3f)
        composeTestRule.activityRule.scenario.recreate()

        composeTestRule.waitUntilTagDisplayed("contacts_list_screen")
        composeTestRule.onNodeWithTag("search_field").assertIsDisplayed()
        composeTestRule.waitUntilTagDisplayed("contacts_empty_state")
        composeTestRule.onNodeWithTag("contacts_empty_state").assertIsDisplayed()
        assertTouchTargetAtLeast48dp("sync_button")
        assertTouchTargetAtLeast48dp("settings_button")

        composeTestRule.navigateToEventsTab()
        composeTestRule.waitUntilTagDisplayed("events_empty_state")
        composeTestRule.onNodeWithTag("events_empty_state").assertIsDisplayed()
        assertTouchTargetAtLeast48dp("add_event_fab")
    }

    // ---------------------------------------------------------------- helpers

    private fun seedContact(): Long = runBlocking {
        contactDao.insert(
            Contact(
                displayName = NAME,
                phoneNumber = PHONE,
                email = EMAIL
            )
        )
    }

    private fun openFirstContactDetail() {
        composeTestRule.waitUntilTagExists("contacts_list_screen")
        composeTestRule.waitUntilTagExists("contact_list_item")
        composeTestRule.onAllNodesWithTag("contact_list_item")[0].performClick()
        composeTestRule.waitUntilTagDisplayed("contact_detail_screen")
    }

    /**
     * Collects Text semantics from the merged semantics tree ordered
     * top-to-bottom, left-to-right — the order TalkBack reads a simple vertical
     * layout in. (A per-level DFS is not enough: Scaffold content spans y=0 with
     * padding applied inside, which can sort below the top bar.)
     */
    private fun readingOrder(): List<Pair<String, Offset>> {
        val texts = mutableListOf<Pair<String, Offset>>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach {
                texts.add(it.text to node.positionInRoot)
            }
            node.children.forEach { walk(it) }
        }
        walk(composeTestRule.onRoot().fetchSemanticsNode())
        return texts.sortedWith(compareBy({ it.second.y }, { it.second.x }))
    }

    private fun assertTouchTargetAtLeast48dp(tag: String) {
        val node = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
        val minPx = with(composeTestRule.density) { 48.dp.toPx() }
        assertTrue(
            "$tag touch target ${node.size} is smaller than 48dp",
            node.size.height >= minPx && node.size.width >= minPx
        )
    }

    /** Changes the system font scale and waits for the settings command to exit. */
    private fun setFontScale(scale: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val fd = automation.executeShellCommand("settings put system font_scale $scale")
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private companion object {
        const val NAME = "Talkback Tina"
        const val PHONE = "+1 555 0100"
        const val EMAIL = "tina@example.com"
        const val FACT_TEXT = "Vegetarian"
        const val NOTE_TEXT = "Coffee chat about birds"
    }
}
