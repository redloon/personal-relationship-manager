package com.redloon.prm.ui

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.redloon.prm.R
import com.redloon.prm.data.backup.AutomaticBackup
import com.redloon.prm.data.backup.DataExporter
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.entity.Event
import com.redloon.prm.data.recovery.RecoveryRepository
import com.redloon.prm.data.settings.SettingsRepository
import com.redloon.prm.ui.settings.RestoreState
import com.redloon.prm.ui.settings.SettingsScreen
import com.redloon.prm.ui.settings.SettingsViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class RestoreFlowTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val ui = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var database: AppDatabase

    @Inject lateinit var exporter: DataExporter

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var automaticBackup: AutomaticBackup

    @Inject lateinit var recovery: RecoveryRepository
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var model: SettingsViewModel
    private lateinit var files: File
    private val store = ViewModelStore()

    @Before fun setup() {
        hilt.inject()
        database.clearAllTables()
        files = File(context.cacheDir, "restore-flow-test").apply { mkdirs() }
        instrumentation.runOnMainSync {
            model = SettingsViewModel(settings, exporter, automaticBackup, recovery)
            store.put("restore", model)
        }
        // Restoring this empty snapshot would remove the subsequently added note.
        val backup = File(files, "before.json").apply {
            writeText(runBlocking { exporter.exportToJson() })
        }
        runBlocking { database.eventDao().insert(Event(note = "Keep until restore succeeds")) }
        instrumentation.runOnMainSync {
            model.readImport(context.contentResolver, Uri.fromFile(backup))
        }
        awaitState(RestoreState.READY)
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync { store.clear() }
        files.deleteRecursively()
    }

    @Test fun cancellingPreviewKeepsCurrentData() {
        instrumentation.runOnMainSync { model.cancelRestore() }
        assertEquals(RestoreState.IDLE, model.restoreState.value)
        assertNull(model.restorePreview.value)
        assertCurrentNoteKept()
    }

    @Test fun failedSafetyExportCannotReplaceCurrentData() {
        // A directory cannot be opened as the safety-copy output file.
        instrumentation.runOnMainSync {
            model.backupAndRestore(context.contentResolver, Uri.fromFile(files))
        }
        awaitState(RestoreState.ERROR)
        assertCurrentNoteKept()
    }

    @Test fun largeTextRestoreDetailsCanScrollAndCancelWithoutReplacingData() {
        // MainActivity supplies the Hilt owner used by nested reminder preferences.
        // Replace its content directly; ComposeTestRule.setContent rejects an activity
        // that already installed its normal application content.
        ui.runOnUiThread {
            ui.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    MaterialTheme { SettingsScreen(onBack = {}, viewModel = model) }
                }
            }
        }
        ui.onNodeWithTag("restore_confirmation_dialog").assertExists()
        ui.onNodeWithText(context.getString(R.string.reliability_restore_explanation))
            .performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("confirm_restore_button").assertIsDisplayed()
        ui.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        ui.onNodeWithTag("restore_confirmation_dialog").assertDoesNotExist()
        assertCurrentNoteKept()
    }

    private fun awaitState(expected: RestoreState) = runBlocking {
        withTimeout(10_000) { model.restoreState.first { it == expected } }
    }

    private fun assertCurrentNoteKept() = runBlocking {
        assertEquals(
            listOf("Keep until restore succeeds"),
            database.eventDao().getAllOnce().map {
                it.note
            }
        )
    }
}
