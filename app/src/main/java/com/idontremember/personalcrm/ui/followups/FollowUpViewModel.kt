package com.idontremember.personalcrm.ui.followups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.followups.FollowUpRepository
import com.idontremember.personalcrm.data.recovery.RecoveryRepository
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class FollowUpViewModel @Inject constructor(
    val repository: FollowUpRepository,
    contacts: ContactRepository,
    val recovery: RecoveryRepository,
    val settings: SettingsRepository
) : ViewModel() {
    val dateDisplay = settings.dateDisplay.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DateDisplay.ABSOLUTE
    )
    val items = repository.items.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val people = contacts.getContactsIncludingHidden().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val prompted = settings.followUpReminderPrompted.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )
    val reminders = settings.followUpRemindersEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )
    val hour = settings.reminderHour.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        9
    )
}
