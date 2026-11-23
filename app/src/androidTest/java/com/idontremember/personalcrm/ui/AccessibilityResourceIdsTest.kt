package com.idontremember.personalcrm.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.tile.QuickAddEventActivity
import com.idontremember.personalcrm.ui.addevent.ShareCaptureActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Validate the external accessibility tree used by ADB QA, not only Compose tags. */
@HiltAndroidTest
class AccessibilityResourceIdsTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val ui = createAndroidComposeRule<MainActivity>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var originalFlags = 0

    @Before fun setup() {
        hilt.inject()
        originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("capture_draft", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After fun restoreFlags() {
        automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
    }

    @Test fun mainAndCaptureSheetExposeStableResourceIds() {
        ui.waitUntilTagDisplayed("dashboard_search", 10_000)
        waitForResourceId("dashboard_search")
        ui.onNodeWithTag("add_event_fab").performClick()
        ui.waitUntilTagDisplayed("note_text_field", 10_000)
        waitForResourceId("note_text_field")
    }

    @Test fun quickCaptureExposesStableResourceIds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<QuickAddEventActivity>(
            Intent(context, QuickAddEventActivity::class.java)
        ).use {
            ui.waitUntilTagDisplayed("note_text_field", 10_000)
            waitForResourceId("note_text_field")
        }
    }

    @Test fun sharedCaptureExposesStableResourceIds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, ShareCaptureActivity::class.java)
            .setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Fictional accessibility trial note")
        ActivityScenario.launch<ShareCaptureActivity>(intent).use {
            ui.waitUntilTagDisplayed("note_text_field", 10_000)
            waitForResourceId("note_text_field")
        }
    }

    @Test fun reminderToggleHasItsVisibleLabelAndOneClickAction() {
        ui.waitUntilTagDisplayed("settings_button", 10_000)
        ui.onNodeWithTag("settings_button").performClick()
        ui.waitUntilTagExists("followup_reminders_toggle")
        ui.onNodeWithTag("followup_reminders_toggle").performScrollTo()
            .assert(hasText(ui.activity.getString(R.string.followup_reminders)))
            .assert(hasClickAction())
        waitForResourceId("followup_reminders_toggle")
    }

    private fun waitForResourceId(id: String) {
        ui.waitUntil(10_000) {
            automation.rootInActiveWindow?.let { containsResourceId(it, id) } == true
        }
    }

    @Suppress("DEPRECATION")
    private fun containsResourceId(node: AccessibilityNodeInfo, id: String): Boolean = try {
        val name = node.viewIdResourceName
        name == id || name?.endsWith(":id/$id") == true || (0 until node.childCount).any { index ->
            node.getChild(index)?.let { containsResourceId(it, id) } == true
        }
    } finally {
        node.recycle()
    }
}
