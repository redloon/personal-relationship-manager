package com.idontremember.personalcrm.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.idontremember.personalcrm.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real IME/layout regression; the integration owner runs this on the dedicated emulator. */
@HiltAndroidTest
class CaptureViewportTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val ui = createAndroidComposeRule<MainActivity>()

    @Test fun longCaptureKeepsTextAndToolsAboveKeyboardAtTwoViewportSizes() {
        hilt.inject()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val originalSize = shell("wm size")
        val originalOverride = Regex(
            "Override size: (\\d+x\\d+)"
        ).find(originalSize)?.groupValues?.get(1)
        val effectiveSize = originalOverride ?: Regex("Physical size: (\\d+x\\d+)")
            .find(originalSize)?.groupValues?.get(1)
            ?: error("Cannot read display size: $originalSize")
        val (width, height) = effectiveSize.split('x').map(String::toInt)
        val largerSize = "${maxOf(480, width * 3 / 2)}x${maxOf(960, height * 3 / 2)}"
        val originalFlags = automation.serviceInfo.flags
        val note =
            "A long remembered detail with useful context. ".repeat(35) + "Latest typed line."
        val tags =
            listOf(
                "note_text_field",
                "save_event_button",
                "capture_people_button",
                "capture_tags_button",
                "capture_date_button",
                "capture_time_button"
            )
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags =
                    flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            for (size in listOf(effectiveSize, largerSize)) {
                if (size != effectiveSize) shell("wm size $size")
                clearDrafts()
                ui.activityRule.scenario.recreate()
                ui.waitUntilTagDisplayed("add_event_fab", 15_000)
                ui.onNodeWithTag("add_event_fab").performClick()
                ui.waitUntilTagDisplayed("note_text_field", 10_000)
                ui.onNodeWithTag("note_text_field").performTextInput(note)
                ui.waitForKeyboard(visible = true)
                // Give sheet/IME resize animations time to settle before comparing bounds.
                ui.mainClock.advanceTimeBy(500)
                ui.waitForIdle()
                ui.onNodeWithTag("note_text_field").assertIsFocused().assertTextContains(note)
                ui.onNodeWithTag("note_text_field").assert(
                    SemanticsMatcher("Caret is at the latest typed text") {
                        it.config[SemanticsProperties.TextSelectionRange].end == note.length
                    }
                )
                tags.forEach { ui.onNodeWithTag(it).assertIsDisplayed() }
                val imeBounds = automation.windows.firstOrNull {
                    it.type ==
                        AccessibilityWindowInfo.TYPE_INPUT_METHOD
                }
                    ?.let { window -> Rect().also(window::getBoundsInScreen) }
                if (imeBounds != null && !imeBounds.isEmpty) {
                    for (tag in tags) {
                        val bounds = automation.windows.asSequence()
                            .filter { it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                            .mapNotNull {
                                it.root?.let { root -> boundsForTag(root, tag) }
                            }.firstOrNull()
                            ?: error("No accessible bounds for $tag at viewport $size")
                        assertTrue(
                            "$tag $bounds crosses keyboard $imeBounds at viewport $size",
                            bounds.bottom <= imeBounds.top + 1
                        )
                        println("Capture viewport $size: $tag=$bounds; keyboard=$imeBounds")
                    }
                } else {
                    println(
                        "Capture viewport $size: IME visible; bounds unavailable, displayed assertions passed"
                    )
                }
                ui.onNodeWithContentDescription(
                    ui.activity.getString(R.string.cd_close)
                ).performClick()
                ui.waitUntilTagDisplayed("add_event_fab", 10_000)
            }
        } finally {
            try {
                shell(originalOverride?.let { "wm size $it" } ?: "wm size reset")
            } finally {
                clearDrafts()
                automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
            }
        }
    }

    private fun boundsForTag(node: AccessibilityNodeInfo, tag: String): Rect? {
        val id = node.viewIdResourceName
        if (id == tag || id?.endsWith("/$tag") == true) return Rect().also(node::getBoundsInScreen)
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child -> boundsForTag(child, tag)?.let { return it } }
        }
        return null
    }

    private fun clearDrafts() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("capture_draft", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).use { it.readBytes().toString(Charsets.UTF_8) }
}
