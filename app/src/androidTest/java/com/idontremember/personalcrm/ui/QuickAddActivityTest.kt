package com.idontremember.personalcrm.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import com.idontremember.personalcrm.data.repository.EventRepository
import com.idontremember.personalcrm.tile.QuickAddEventActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class QuickAddActivityTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<QuickAddEventActivity>()
    @Inject lateinit var events: EventRepository

    @Before fun setup() {
        hiltRule.inject()
        composeRule.activity.getSharedPreferences("capture_draft", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        composeRule.activityRule.scenario.recreate()
        runBlocking { events.getAllEvents().first().forEach { events.deleteEvent(it) } }
    }

    @Test fun tileActivityStartsReadyAndSavesOnce() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            try { composeRule.onNodeWithTag("note_text_field").assertIsFocused(); true }
            catch (_: AssertionError) { false }
        }
        composeRule.onNodeWithTag("note_text_field").performTextInput("Tile capture")
        composeRule.onNodeWithTag("save_event_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { events.getAllEvents().first().count { it.note == "Tile capture" } == 1 }
        }
        assertEquals(1, runBlocking { events.getAllEvents().first().count { it.note == "Tile capture" } })
    }
}
