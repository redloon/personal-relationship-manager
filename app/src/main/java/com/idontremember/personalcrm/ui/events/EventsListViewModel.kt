package com.idontremember.personalcrm.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.dao.TagDao
import com.idontremember.personalcrm.data.db.dao.EventDao
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventDisplayItem(
    val event: Event,
    val contacts: List<Contact>,
    val tags: List<Tag>
)

@HiltViewModel
class EventsListViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val eventDao: EventDao,
    private val contactDao: ContactDao,
    private val tagDao: TagDao
) : ViewModel() {

    private val _events = MutableStateFlow<List<EventDisplayItem>>(emptyList())
    val events: StateFlow<List<EventDisplayItem>> = _events

    init {
        loadEvents()
    }

    private fun loadEvents() {
        viewModelScope.launch {
            eventRepository.getAllEvents().collect { eventList ->
                val displayItems = eventList.map { event ->
                    val contactIds = eventDao.getContactIdsForEvent(event.id)
                    val tagIds = eventDao.getTagIdsForEvent(event.id)
                    val contacts = contactIds.mapNotNull { contactDao.getById(it) }
                    val tags = tagIds.mapNotNull { tagId ->
                        tagDao.getAllOnce().find { it.id == tagId }
                    }
                    EventDisplayItem(event = event, contacts = contacts, tags = tags)
                }
                _events.value = displayItems
            }
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            eventRepository.deleteEvent(event)
        }
    }
}