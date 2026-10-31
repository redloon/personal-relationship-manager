package com.idontremember.personalcrm.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.dao.EventDao
import com.idontremember.personalcrm.data.db.dao.TagDao
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.repository.EventRepository
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
class EventDetailViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val eventDao: EventDao,
    private val contactDao: ContactDao,
    private val tagDao: TagDao,
    settingsRepository: SettingsRepository
) : ViewModel() {

    /** P1 preference: absolute vs. relative rendering of event timestamps. */
    val dateDisplay: StateFlow<DateDisplay> = settingsRepository.dateDisplay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DateDisplay.ABSOLUTE)

    private var eventId: Long = 0L

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event

    /** P2: false until the load completes — drives the shared `LoadingState`;
     *  a null event after this is "not found" and renders the shared `ErrorState`. */
    private val _eventLoaded = MutableStateFlow(false)
    val eventLoaded: StateFlow<Boolean> = _eventLoaded

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    val availableContacts: StateFlow<List<Contact>> = contactDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editContactIds = MutableStateFlow<Set<Long>>(emptySet())
    val editContactIds: StateFlow<Set<Long>> = _editContactIds

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing

    private val _editNote = MutableStateFlow("")
    val editNote: StateFlow<String> = _editNote

    private val _editSaveFailed = MutableStateFlow(false)
    val editSaveFailed: StateFlow<Boolean> = _editSaveFailed

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    fun setEventId(id: Long) {
        if (eventId != 0L) return
        eventId = id
        loadEvent()
    }

    private fun loadEvent() {
        viewModelScope.launch {
            val e = eventRepository.getEventById(eventId)
            _event.value = e
            _eventLoaded.value = true
            e?.let {
                _editNote.value = it.note
                val contactIds = eventDao.getContactIdsForEvent(it.id)
                val tagIds = eventDao.getTagIdsForEvent(it.id)
                _contacts.value = contactIds.mapNotNull { cid -> contactDao.getById(cid) }
                _tags.value = tagIds.mapNotNull { tid ->
                    tagDao.getAllOnce().find { t -> t.id == tid }
                }
            }
        }
    }

    fun startEditing() {
        _isEditing.value = true
        _editNote.value = _event.value?.note ?: ""
        _editContactIds.value = _contacts.value.map { it.id }.toSet()
        _editSaveFailed.value = false
    }

    fun addEditContact(id: Long) { _editContactIds.value = _editContactIds.value + id }
    fun removeEditContact(id: Long) { _editContactIds.value = _editContactIds.value - id }

    fun onNoteChanged(note: String) {
        _editNote.value = note
    }

    fun saveEdit(note: String, tagNames: List<String>) {
        viewModelScope.launch {
            val e = _event.value ?: return@launch
            try {
                eventRepository.updateEvent(
                    eventId = e.id,
                    note = note,
                    contactIds = _editContactIds.value.toList(),
                    tagNames = tagNames,
                    eventDate = e.eventDate
                )
                _isEditing.value = false
                loadEvent()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (_: Exception) { _editSaveFailed.value = true }
        }
    }

    fun deleteEvent() {
        viewModelScope.launch {
            _event.value?.let { eventRepository.deleteEvent(it) }
            _saved.value = true
        }
    }

    fun cancelEditing() {
        _isEditing.value = false
        _editContactIds.value = emptySet()
        _editSaveFailed.value = false
    }

    /** P2: retry hook for the shared `ErrorState` after a failed/missing load. */
    fun retryLoad() {
        loadEvent()
    }
}
