package com.idontremember.personalcrm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.settings.DefaultEventTime
import com.idontremember.personalcrm.data.settings.SettingsRepository
import com.idontremember.personalcrm.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Settings screen. Each preference is exposed as collected state and a
 * setter; writes go straight to [SettingsRepository] (Preferences DataStore) so
 * they survive process death and are observed reactively by the theme and screens.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val dynamicColor: StateFlow<Boolean> = settingsRepository.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val dateDisplay: StateFlow<DateDisplay> = settingsRepository.dateDisplay
        .stateIn(viewModelScope, SharingStarted.Eagerly, DateDisplay.ABSOLUTE)

    val defaultEventTime: StateFlow<DefaultEventTime> = settingsRepository.defaultEventTime
        .stateIn(viewModelScope, SharingStarted.Eagerly, DefaultEventTime.NOW)

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(value) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setDateDisplay(value: DateDisplay) {
        viewModelScope.launch { settingsRepository.setDateDisplay(value) }
    }

    fun setDefaultEventTime(value: DefaultEventTime) {
        viewModelScope.launch { settingsRepository.setDefaultEventTime(value) }
    }
}
