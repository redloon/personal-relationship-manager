package com.redloon.prm.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redloon.prm.data.db.dao.ContactFactDao
import com.redloon.prm.data.recovery.RecoveryRepository
import com.redloon.prm.data.repository.ContactRepository
import com.redloon.prm.data.settings.DateDisplay
import com.redloon.prm.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class FactReaderViewModel @Inject constructor(
    val facts: ContactFactDao,
    val contacts: ContactRepository,
    val recovery: RecoveryRepository,
    settings: SettingsRepository
) : ViewModel() {
    val dateDisplay = settings.dateDisplay.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DateDisplay.ABSOLUTE
    )
}
