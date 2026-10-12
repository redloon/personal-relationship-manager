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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val eventDao: EventDao,
    private val contactDao: ContactDao,
    private val tagDao: TagDao
) : ViewModel() {

    private var eventId: Long = 0L

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing

    private val _editNote = MutableStateFlow("")
    val editNote: StateFlow<String> = _editNote

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
    }

    fun onNoteChanged(note: String) {
        _editNote.value = note
    }

    fun saveEdit(note: String, contactIds: List<Long>, tagNames: List<String>) {
        viewModelScope.launch {
            val e = _event.value ?: return@launch
            eventRepository.updateEvent(
                eventId = e.id,
                note = note,
                contactIds = contactIds,
                tagNames = tagNames,
                eventDate = e.eventDate
            )
            _isEditing.value = false
            loadEvent() // Refresh
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
    }
}