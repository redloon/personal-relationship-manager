package com.idontremember.personalcrm.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.backup.BackupJson
import com.idontremember.personalcrm.data.backup.BackupSnapshot
import com.idontremember.personalcrm.data.backup.DataExporter
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.settings.DefaultEventTime
import com.idontremember.personalcrm.data.settings.SettingsRepository
import com.idontremember.personalcrm.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ExportState { IDLE, EXPORTING, DONE, ERROR }
enum class RestoreState { IDLE, LOADING, READY, RESTORING, DONE, ERROR }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dataExporter: DataExporter
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val dynamicColor: StateFlow<Boolean> = settingsRepository.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val dateDisplay: StateFlow<DateDisplay> = settingsRepository.dateDisplay
        .stateIn(viewModelScope, SharingStarted.Eagerly, DateDisplay.ABSOLUTE)
    val defaultEventTime: StateFlow<DefaultEventTime> = settingsRepository.defaultEventTime
        .stateIn(viewModelScope, SharingStarted.Eagerly, DefaultEventTime.NOW)

    private val _exportState = MutableStateFlow(ExportState.IDLE)
    val exportState: StateFlow<ExportState> = _exportState
    private val _restoreState = MutableStateFlow(RestoreState.IDLE)
    val restoreState: StateFlow<RestoreState> = _restoreState
    private val _restorePreview = MutableStateFlow<BackupSnapshot?>(null)
    val restorePreview: StateFlow<BackupSnapshot?> = _restorePreview

    fun setThemeMode(value: ThemeMode) { viewModelScope.launch { settingsRepository.setThemeMode(value) } }
    fun setDynamicColor(value: Boolean) { viewModelScope.launch { settingsRepository.setDynamicColor(value) } }
    fun setDateDisplay(value: DateDisplay) { viewModelScope.launch { settingsRepository.setDateDisplay(value) } }
    fun setDefaultEventTime(value: DefaultEventTime) { viewModelScope.launch { settingsRepository.setDefaultEventTime(value) } }

    fun exportTo(resolver: ContentResolver, uri: Uri) {
        if (_exportState.value == ExportState.EXPORTING) return
        _exportState.value = ExportState.EXPORTING
        viewModelScope.launch {
            try {
                val json = dataExporter.exportToJson()
                withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(uri, "wt") ?: error("Cannot open backup file")
                    output.use { it.write(json.toByteArray(Charsets.UTF_8)); it.flush() }
                }
                _exportState.value = ExportState.DONE
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _exportState.value = ExportState.ERROR }
        }
    }

    fun readImport(resolver: ContentResolver, uri: Uri) {
        if (_restoreState.value == RestoreState.LOADING || _restoreState.value == RestoreState.RESTORING) return
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
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _restoreState.value = RestoreState.ERROR }
        }
    }

    fun confirmRestore() {
        val snapshot = _restorePreview.value ?: return
        if (_restoreState.value != RestoreState.READY) return
        _restoreState.value = RestoreState.RESTORING
        viewModelScope.launch {
            try {
                dataExporter.restore(snapshot)
                _restorePreview.value = null
                _restoreState.value = RestoreState.DONE
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _restoreState.value = RestoreState.ERROR }
        }
    }

    fun cancelRestore() { _restorePreview.value = null; _restoreState.value = RestoreState.IDLE }
    fun clearExportFeedback() { _exportState.value = ExportState.IDLE }
    fun clearRestoreFeedback() { _restoreState.value = RestoreState.IDLE }
}
