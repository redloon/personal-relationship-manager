package com.idontremember.personalcrm.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.contacts.InteractionRepository
import com.idontremember.personalcrm.data.contacts.InteractionSnapshot
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.recovery.RecoveryRepository
import com.idontremember.personalcrm.data.recovery.TrashKind
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val recovery: RecoveryRepository,
    interactions: InteractionRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    /** P1 preference: absolute vs. relative rendering of event timestamps. */
    val dateDisplay: StateFlow<DateDisplay> = settingsRepository.dateDisplay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DateDisplay.ABSOLUTE)

    // Set after creation via setContactId()
    private var contactId: Long = 0L

    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact

    /** P2: false until the first query emission — drives the shared `LoadingState`;
     *  a null contact after this is "not found" and renders the shared `ErrorState`. */
    val interactionSnapshot: StateFlow<InteractionSnapshot?> = interactions.snapshots.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    private val _contactLoaded = MutableStateFlow(false)
    val contactLoaded: StateFlow<Boolean> = _contactLoaded

    private val _facts = MutableStateFlow<List<ContactFact>>(emptyList())
    val facts: StateFlow<List<ContactFact>> = _facts

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    fun setContactId(id: Long) {
        if (contactId != 0L) return // Already set
        contactId = id
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            contactRepository.getContactByIdFlow(contactId).collect {
                _contact.value = it
                _contactLoaded.value = true
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

    private val _deletedFact = MutableStateFlow<Long?>(null)
    val deletedFact: StateFlow<Long?> = _deletedFact
    private val _operationError = MutableStateFlow(false)
    val operationError: StateFlow<Boolean> = _operationError
    private val _linking = MutableStateFlow(false)
    val linking: StateFlow<Boolean> = _linking

    fun deleteFact(fact: ContactFact) {
        viewModelScope.launch {
            try {
                recovery.delete(TrashKind.FACT, fact.id)
                _deletedFact.value = fact.id
            } catch (
                e: kotlinx.coroutines.CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                _operationError.value = true
            }
        }
    }
    fun consumeDeletedFact() {
        _deletedFact.value = null
    }
    fun restoreFact(id: Long) {
        viewModelScope.launch {
            try {
                recovery.restore(TrashKind.FACT, id)
            } catch (
                e: kotlinx.coroutines.CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                _operationError.value = true
            }
        }
    }
    fun clearOperationError() {
        _operationError.value = false
    }
    fun rename(name: String, onSaved: () -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                contactRepository.renameLocalContact(contactId, name.trim())
                onSaved()
            } catch (
                e: kotlinx.coroutines.CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                _operationError.value = true
            }
        }
    }
    fun linkAndroidContact(androidId: Long) {
        if (_linking.value) return
        _linking.value = true
        viewModelScope.launch {
            try {
                contactRepository.linkAndroidContact(contactId, androidId)
            } catch (
                e: kotlinx.coroutines.CancellationException
            ) {
                throw e
            } catch (
                _: Exception
            ) {
                _operationError.value = true
            } finally {
                _linking.value = false
            }
        }
    }
}
