package com.idontremember.personalcrm.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.db.dao.ContactFactDao
import com.idontremember.personalcrm.data.recovery.RecoveryRepository
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.settings.SettingsRepository
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
