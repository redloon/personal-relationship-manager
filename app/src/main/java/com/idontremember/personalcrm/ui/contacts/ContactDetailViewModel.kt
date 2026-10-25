package com.idontremember.personalcrm.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    /** P1 preference: absolute vs. relative rendering of event timestamps. */
    val dateDisplay: StateFlow<DateDisplay> = settingsRepository.dateDisplay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DateDisplay.ABSOLUTE)

    // Set after creation via setContactId()
    private var contactId: Long = 0L

    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact

    private val _facts = MutableStateFlow<List<ContactFact>>(emptyList())
    val facts: StateFlow<List<ContactFact>> = _facts

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    private val _showAddFactDialog = MutableStateFlow(false)
    val showAddFactDialog: StateFlow<Boolean> = _showAddFactDialog

    fun setContactId(id: Long) {
        if (contactId != 0L) return // Already set
        contactId = id
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            contactRepository.getContactByIdFlow(contactId).collect {
                _contact.value = it
            }
        }
        viewModelScope.launch {
            contactRepository.getFactsForContact(contactId).collect {
                _facts.value = it
            }
        }
        viewModelScope.launch {
            contactRepository.getEventsForContact(contactId).collect {
                _events.value = it
            }
        }
    }

    fun onAddFactClick() {
        _showAddFactDialog.value = true
    }

    fun onDismissAddFact() {
        _showAddFactDialog.value = false
    }

    fun addFact(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            contactRepository.addFact(contactId, text)
            _showAddFactDialog.value = false
        }
    }

    fun deleteFact(fact: ContactFact) {
        viewModelScope.launch {
            contactRepository.deleteFact(fact)
        }
    }
}