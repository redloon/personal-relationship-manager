package com.redloon.prm.ui

import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.settings.SettingsRepository
import com.redloon.prm.data.settings.ThemeMode
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HiddenPeopleHeaderTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var db: AppDatabase

    @Inject lateinit var settings: SettingsRepository
    private var first = 0L
    private var last = 0L

    @Before fun setup() {
        hilt.inject()
        db.clearAllTables()
        runBlocking {
            settings.setThemeMode(ThemeMode.LIGHT)
            settings.setDynamicColor(false)
            settings.setAutomaticContactSyncEnabled(false)
            first =
                db.contactDao().insert(Contact(displayName = "Hidden person 00", isHidden = true))
            for (i in 1..24) {
                last =
                    db.contactDao().insert(
                        Contact(displayName = "Hidden person %02d".format(i), isHidden = true)
                    )
            }
        }
        compose.navigateToContactsTab()
    }

    @After fun cleanup() {
        setFontScale(1f)
        runBlocking {
            settings.setThemeMode(ThemeMode.SYSTEM)
            settings.setDynamicColor(true)
            settings.setAutomaticContactSyncEnabled(false)
        }
        db.clearAllTables()
    }

    @Test fun peopleUsesLabelledSyncOverflowWithoutAStandaloneIcon() {
        compose.onNodeWithTag("sync_button").assertDoesNotExist()
        compose.onNodeWithTag("people_menu").performClick()
        compose.onNodeWithTag("contacts_sync_menu").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun hiddenIdentitySurvivesScrollingSearchSelectionRecreationAndBack() {
        openHidden()
        assertIdentity()
        compose.onNodeWithTag("contacts_list").performScrollToNode(hasTestTag("person_row_$last"))
        assertIdentity()
        compose.onNodeWithTag("person_row_$last").performTouchInput { longClick() }
        assertIdentity()
        compose.onNodeWithTag("people_selection_count").assertTextEquals("1 selected")
        compose.onNodeWithTag("search_field").performTextInput("no such person")
        compose.waitUntilTagDisplayed("contacts_empty_state")
        assertIdentity()
        compose.activityRule.scenario.recreate()
        compose.waitUntilTagDisplayed("people_selection_count")
        assertIdentity()
        compose.onNodeWithTag("people_selection_count").assertTextEquals("1 selected")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilTagDisplayed("hidden_people_back")
        assertIdentity()
        compose.onNodeWithTag("hidden_people_back").performClick()
        compose.waitUntilTagDisplayed("contacts_list_screen")
        compose.onNodeWithTag("people_context_title").assertDoesNotExist()
    }

    @Test fun hiddenHeaderAndActionsFitLargeTextInBothThemesAndDynamicColors() {
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            for (dynamic in listOf(false, true)) {
                for (scale in listOf(1f, 1.3f, 2f)) {
                    runBlocking {
                        settings.setThemeMode(theme)
                        settings.setDynamicColor(dynamic)
                    }
                    setFontScale(scale)
                    compose.activityRule.scenario.recreate()
                    compose.navigateToContactsTab()
                    val ordinary = headerColor()
                    openHidden()
                    assertIdentity()
                    assertWholeText("people_context_title")
                    val hidden = headerColor()
                    assertNotEquals(
                        "Hidden header must differ from People ($theme, dynamic=$dynamic, scale=$scale)",
                        ordinary,
                        hidden
                    )
                    compose.onNodeWithTag("person_row_$first").performTouchInput { longClick() }
                    assertIdentity()
                    assertWholeText("people_context_title")
                    assertWholeText("people_selection_count")
                    assertEquals(hidden, headerColor())
                    compose.onNodeWithTag(
                        "people_visibility_action"
                    ).assertIsDisplayed().assertIsEnabled()
                    compose.onNodeWithTag(
                        "people_selection_close"
                    ).assertIsDisplayed().performClick()
                    compose.onNodeWithTag("hidden_people_back").performClick()
                    compose.waitUntilTagDisplayed("contacts_list_screen")
                }
            }
        }
    }

    private fun openHidden() {
        compose.onNodeWithTag("people_menu").performClick()
        compose.onNodeWithTag("hidden_people_menu").performClick()
        compose.waitUntilTagDisplayed("hidden_people_screen")
        compose.waitUntilTagDisplayed("person_row_$first")
    }

    private fun assertIdentity() {
        compose.onNodeWithTag(
            "people_context_title"
        ).assertIsDisplayed().assertTextEquals("Hidden people")
        compose.onNodeWithTag("people_context_icon", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun assertWholeText(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val layout = layouts.single()
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) {
            assertFalse("$tag must not ellipsize", layout.isLineEllipsized(line))
            assertTrue(
                "$tag must fit its width",
                layout.getLineRight(line) <= layout.size.width + 1
            )
            assertTrue(
                "$tag must fit its height",
                layout.getLineBottom(line) <= layout.size.height + 1
            )
        }
    }

    private fun headerColor() =
        compose.onNodeWithTag("people_appbar").captureToImage().toPixelMap()[1, 1]

    private fun setFontScale(scale: Float) {
        val command = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "settings put system font_scale $scale"
        )
        ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
    }
}
