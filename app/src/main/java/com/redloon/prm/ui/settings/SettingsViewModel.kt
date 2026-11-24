package com.redloon.prm.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redloon.prm.data.backup.AutomaticBackup
import com.redloon.prm.data.backup.BackupJson
import com.redloon.prm.data.backup.BackupSnapshot
import com.redloon.prm.data.backup.DataExporter
import com.redloon.prm.data.recovery.RecoveryRepository
import com.redloon.prm.data.recovery.TrashKind
import com.redloon.prm.data.settings.DateDisplay
import com.redloon.prm.data.settings.DefaultEventTime
import com.redloon.prm.data.settings.SettingsRepository
import com.redloon.prm.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportState { IDLE, EXPORTING, DONE, ERROR }
enum class RestoreState { IDLE, LOADING, READY, RESTORING, DONE, ERROR }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dataExporter: DataExporter,
    private val automaticBackup: AutomaticBackup,
    private val recovery: RecoveryRepository
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val dynamicColor: StateFlow<Boolean> = settingsRepository.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val dateDisplay: StateFlow<DateDisplay> = settingsRepository.dateDisplay
        .stateIn(viewModelScope, SharingStarted.Eagerly, DateDisplay.ABSOLUTE)
    val defaultEventTime: StateFlow<DefaultEventTime> = settingsRepository.defaultEventTime
        .stateIn(viewModelScope, SharingStarted.Eagerly, DefaultEventTime.NOW)

    val backupStatus = automaticBackup.status.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.redloon.prm.data.backup.BackupStatus(null, null, false)
    )
    val deletedItems = recovery.observeDeleted().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy
    private val _recoveryFailed = MutableStateFlow(false)
    val recoveryFailed: StateFlow<Boolean> = _recoveryFailed

    init {
        viewModelScope.launch {
            try {
                recovery.purgeExpired()
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                _recoveryFailed.value = true
            }
        }
    }

    fun setBackupFolder(uri: Uri?) {
        viewModelScope.launch {
            try {
                automaticBackup.setFolder(uri)
                if (uri != null) backUpNow()
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                _exportState.value = ExportState.ERROR
            }
        }
    }

    fun backUpNow() {
        if (_backupBusy.value) return
        _backupBusy.value = true
        viewModelScope.launch {
            try {
                automaticBackup.backUpNow()
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun recover(kind: TrashKind, id: Long) {
        viewModelScope.launch {
            try {
                recovery.restore(kind, id)
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                _recoveryFailed.value = true
            }
        }
    }
    fun clearRecoveryError() {
        _recoveryFailed.value = false
    }

    private val _exportState = MutableStateFlow(ExportState.IDLE)
    val exportState: StateFlow<ExportState> = _exportState
    private val _restoreState = MutableStateFlow(RestoreState.IDLE)
    val restoreState: StateFlow<RestoreState> = _restoreState
    private val _restorePreview = MutableStateFlow<BackupSnapshot?>(null)
    val restorePreview: StateFlow<BackupSnapshot?> = _restorePreview

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(value) }
    }
    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(value) }
    }
    fun setDateDisplay(value: DateDisplay) {
        viewModelScope.launch { settingsRepository.setDateDisplay(value) }
    }
    fun setDefaultEventTime(value: DefaultEventTime) {
        viewModelScope.launch { settingsRepository.setDefaultEventTime(value) }
    }

    fun exportTo(resolver: ContentResolver, uri: Uri) {
        if (_exportState.value == ExportState.EXPORTING) return
        _exportState.value = ExportState.EXPORTING
        viewModelScope.launch {
            try {
                val json = dataExporter.exportToJson()
                withContext(Dispatchers.IO) {
                    val output =
                        resolver.openOutputStream(uri, "wt") ?: error("Cannot open backup file")
                    output.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                        it.flush()
                    }
                }
                _exportState.value = ExportState.DONE
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _exportState.value = ExportState.ERROR
            }
        }
    }

    fun readImport(resolver: ContentResolver, uri: Uri) {
        if (_restoreState.value == RestoreState.LOADING ||
            _restoreState.value == RestoreState.RESTORING
        ) {
            return
        }
        _restorePreview.value = null
        _restoreState.value = RestoreState.LOADING
        viewModelScope.launch {
            try {
                val source = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("Cannot open backup file")
                    input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                _restorePreview.value = BackupJson.decode(source)
                _restoreState.value = RestoreState.READY
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _restoreState.value = RestoreState.ERROR
            }
        }
    }

    fun backupAndRestore(resolver: ContentResolver, safetyUri: Uri) {
        val snapshot = _restorePreview.value ?: return
        if (_restoreState.value != RestoreState.READY) return
        _restoreState.value = RestoreState.RESTORING
        viewModelScope.launch {
            try {
                val safety = dataExporter.exportToJson()
                withContext(Dispatchers.IO) {
                    val output =
                        resolver.openOutputStream(safetyUri, "wt")
                            ?: error("Cannot open safety backup")
                    output.use {
                        it.write(safety.toByteArray(Charsets.UTF_8))
                        it.flush()
                    }
                    val saved =
                        resolver.openInputStream(safetyUri)?.bufferedReader(Charsets.UTF_8)?.use {
                            it.readText()
                        }
                            ?: error("Cannot verify safety backup")
                    require(BackupJson.decode(saved) == BackupJson.decode(safety))
                }
                dataExporter.restore(snapshot)
                _restorePreview.value = null
                _restoreState.value = RestoreState.DONE
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _restoreState.value = RestoreState.ERROR
            }
        }
    }

    fun cancelRestore() {
        _restorePreview.value = null
        _restoreState.value = RestoreState.IDLE
    }
    fun clearExportFeedback() {
        _exportState.value = ExportState.IDLE
    }
    fun clearRestoreFeedback() {
        _restoreState.value = RestoreState.IDLE
    }
}
