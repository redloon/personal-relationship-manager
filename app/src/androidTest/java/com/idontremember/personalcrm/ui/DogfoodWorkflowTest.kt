package com.idontremember.personalcrm.ui

import android.content.ContentUris
import android.content.ContentValues
import android.os.SystemClock
import android.provider.ContactsContract
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.*
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.settings.SettingsRepository
import com.idontremember.personalcrm.data.settings.DateDisplay
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidTest
class DogfoodWorkflowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var contacts: ContactRepository
    @Inject lateinit var settings: SettingsRepository
    private var maya = 0L
    private var linked = 0L
    private var note = 0L
    private val rawIds = mutableListOf<Long>()

    @Before fun setup() {
        hilt.inject(); db.clearAllTables()
        ui.activity.getSharedPreferences("capture_draft", 0).edit().clear().commit()
        runBlocking {
            settings.setDateDisplay(DateDisplay.ABSOLUTE)
            maya = db.contactDao().insert(Contact(displayName = "Maya Typo", phoneNumber = "5550100", email = "maya@example.com"))
            linked = db.contactDao().insert(Contact(displayName = "Alex Android", androidContactId = 424242))
            db.contactDao().insert(Contact(displayName = "Hidden local", isHidden = true))
            note = db.eventDao().insert(Event(note = "Lantern bakery", eventDate = 1_700_000_000_000))
            db.eventDao().insertEventContact(EventContact(note, maya))
        }
    }
    @After fun cleanup() {
        val resolver = ui.activity.contentResolver
        rawIds.forEach { resolver.delete(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, it), null, null) }
        db.clearAllTables()
    }

    @Test fun directorySearchUsesContactDetailsAndHomeStillFindsContext() {
        ui.navigateToContactsTab()
        ui.onNodeWithTag("search_field").performTextInput("Lantern")
        ui.waitUntilTagDisplayed("contacts_empty_state")
        ui.onNodeWithTag("clear_contacts_search_button").performClick()
        ui.onNodeWithTag("search_field").performTextInput("CHEN example")
        ui.waitUntilTagDisplayed("contacts_empty_state")
        ui.onNodeWithTag("clear_contacts_search_button").performClick()
        ui.onNodeWithTag("search_field").performTextInput("maya@example.com")
        ui.waitUntilTagDisplayed("person_row_$maya")
        ui.onNodeWithTag("person_row_$maya").performTouchInput { click() }
        ui.waitUntilTagDisplayed("person_search")
        ui.onNodeWithTag("person_search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntilTagDisplayed("search_field")
        ui.onNodeWithTag("search_field").assertTextContains("maya@example.com")
        ui.onNodeWithTag("nav_tab_home").performClick()
        ui.onNodeWithTag("dashboard_search").performTextInput("Lantern")
        ui.waitUntilTagDisplayed("recall_event_$note")
        ui.onNodeWithTag("recall_event_$note").performClick()
        ui.waitUntilTagDisplayed("event_detail_screen")
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntilTagDisplayed("dashboard_search")
        ui.onNodeWithTag("dashboard_search").assertTextContains("Lantern")
    }

    @Test fun linkedAndUnlinkedProfileActionsAreContextual() {
        ui.navigateToContactsTab()
        ui.onNodeWithTag("person_row_$linked").performTouchInput { click() }
        ui.waitUntilTagDisplayed("edit_person_button")
        ui.onNodeWithTag("edit_person_button").performClick()
        ui.onNodeWithTag("open_android_contact").assertIsDisplayed()
        ui.onNodeWithTag("link_android_contact").assertDoesNotExist()
    }

    @Test fun appOnlyManagementRenamesAndLinkingKeepsContextAndAdoptsAndroidName() {
        ui.navigateToContactsTab()
        ui.onNodeWithTag("people_menu").performClick()
        ui.onNodeWithTag("app_only_people_menu").performClick()
        ui.waitUntilTagDisplayed("app_only_people_screen")
        ui.onNodeWithTag("person_row_$linked").assertDoesNotExist()
        ui.onNodeWithText("Hidden local").assertDoesNotExist()
        ui.onNodeWithTag("person_row_$maya").performTouchInput { click() }
        ui.waitUntilTagDisplayed("edit_person_button")
        ui.onNodeWithTag("edit_person_button").performClick()
        ui.onNodeWithTag("open_android_contact").assertDoesNotExist()
        ui.onNodeWithTag("rename_person_name").performTextReplacement("Maya Chen")
        ui.onNodeWithTag("save_person_name").performClick()
        ui.waitUntil(5_000) { runBlocking { contacts.getContactById(maya)?.displayName == "Maya Chen" } }
        assertEquals(listOf(maya), runBlocking { db.eventDao().getContactIdsForEvent(note) })
        assertEquals("Lantern bakery", runBlocking { db.eventDao().getById(note)!!.note })
        val androidId = createAndroidPerson("Dogfood Android Canonical")
        runBlocking { contacts.linkAndroidContact(maya, androidId) }
        ui.onNodeWithTag("edit_person_button").performClick()
        ui.onNodeWithTag("open_android_contact").assertIsDisplayed()
        ui.onNodeWithTag("link_android_contact").assertDoesNotExist()
        ui.onNodeWithTag("save_person_name").performClick()
        assertEquals("Dogfood Android Canonical", runBlocking { contacts.getContactById(maya)!!.displayName })
        assertEquals(listOf(maya), runBlocking { db.eventDao().getContactIdsForEvent(note) })
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntilTagDisplayed("app_only_people_screen")
        ui.waitUntilTagDisplayed("contacts_empty_state")
    }

    @Test fun lastInteractionUsesEventsAndUpdatesAfterDeletion() {
        runBlocking {
            db.contactFactDao().insert(ContactFact(contactId = maya, text = "A later fact", createdAt = 1_900_000_000_000))
        }
        ui.navigateToContactsTab()
        ui.onNodeWithTag("person_row_$maya").performTouchInput { click() }
        ui.waitUntilTagDisplayed("last_interaction")
        val format = SimpleDateFormat(ui.activity.getString(R.string.date_pattern_event), Locale.getDefault())
        ui.onNodeWithTag("last_interaction").assertTextContains(format.format(Date(1_700_000_000_000)), substring = true)
        runBlocking { val old = db.eventDao().getById(note)!!; db.eventDao().update(old.copy(deletedAt = 1)) }
        ui.waitUntil(5_000) { ui.onAllNodesWithText("No interactions recorded", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun keyboardTouchesDoNotDismissMentionFilteringAndConsecutivePeopleStayIndependent() {
        ui.waitUntilTagDisplayed("add_event_fab")
        ui.onNodeWithTag("add_event_fab").performClick()
        ui.waitUntilTagDisplayed("note_text_field")
        ui.waitForKeyboard(true)
        ui.onNodeWithTag("note_text_field").performTextInput("@")
        ui.onNodeWithText("Maya Typo").assertIsDisplayed()
        assertSuggestionsAvoidKeyboardAndField()
        tapImeSpacebar()
        ui.onNodeWithTag("note_text_field").performTextInput("M")
        ui.onNodeWithText("Maya Typo").assertIsDisplayed()
        ui.onNodeWithTag("note_text_field").performTextInput("a")
        ui.onNodeWithText("Maya Typo").performClick()
        ui.onNodeWithTag("note_text_field").performTextInput("and @A")
        ui.onNodeWithText("Alex Android").performClick()
        ui.onNodeWithText("@Maya Typo").assertExists()
        ui.onNodeWithText("@Alex Android").assertExists()
        ui.onNodeWithTag("save_event_button").performClick()
        ui.waitUntil(5_000) { runBlocking { db.eventDao().getAllOnce().size == 2 } }
        val saved = runBlocking { db.eventDao().getAllOnce().first { it.id != note } }
        assertEquals(setOf(maya, linked), runBlocking { db.eventDao().getContactIdsForEvent(saved.id).toSet() })
    }

    private fun assertSuggestionsAvoidKeyboardAndField() {
        val menu = ui.onNodeWithTag("mention_suggestions").fetchSemanticsNode()
        val field = ui.onNodeWithTag("note_text_field").fetchSemanticsNode()
        val menuTop = menu.positionOnScreen.y
        val menuBottom = menuTop + menu.size.height
        val save = ui.onNodeWithTag("save_event_button").fetchSemanticsNode()
        assertTrue("Suggestions must leave Save available",
            menu.positionOnScreen.x + menu.size.width <= save.positionOnScreen.x + 1 ||
                menuTop >= save.positionOnScreen.y + save.size.height - 1 ||
                menuBottom <= save.positionOnScreen.y + 1)
        var keyboardTop = 0
        ui.runOnUiThread {
            val decor = ui.activity.window.decorView
            val ime = ViewCompat.getRootWindowInsets(decor)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
            assertTrue("The regression requires a real visible keyboard", ime > 0)
            val location = IntArray(2)
            decor.getLocationOnScreen(location)
            keyboardTop = location[1] + decor.height - ime
        }
        assertTrue("Suggestions bottom $menuBottom must stay above keyboard $keyboardTop", menuBottom <= keyboardTop + 1)
        assertTrue("Suggestions must not obscure the typing field",
            menuBottom <= field.positionOnScreen.y + 1 || menuTop >= field.positionOnScreen.y + field.size.height - 1)
    }

    private fun tapImeSpacebar() {
        var x = 0f; var y = 0f
        ui.runOnUiThread {
            val decor = ui.activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)!!
            assertTrue(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0)
            val density = ui.activity.resources.displayMetrics.density
            x = decor.width / 2f
            y = decor.height - insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom - 24 * density
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
        try { automation.injectInputEvent(down, true); automation.injectInputEvent(up, true) }
        finally { down.recycle(); up.recycle() }
        ui.waitForIdle()
    }

    private fun createAndroidPerson(name: String): Long {
        val resolver = ui.activity.contentResolver
        val raw = ContentUris.parseId(resolver.insert(ContactsContract.RawContacts.CONTENT_URI, ContentValues().apply {
            putNull(ContactsContract.RawContacts.ACCOUNT_NAME); putNull(ContactsContract.RawContacts.ACCOUNT_TYPE)
        })!!)
        rawIds += raw
        resolver.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, raw)
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
        })
        var id = 0L
        ui.waitUntil(10_000) {
            resolver.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.CONTACT_ID), "${ContactsContract.RawContacts._ID} = ?", arrayOf(raw.toString()), null)?.use { if (it.moveToFirst()) id = it.getLong(0) }
            id > 0 && resolver.query(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { it.moveToFirst() && it.getString(0) == name } == true
        }
        return id
    }
}
