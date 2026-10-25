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
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventDisplayItem(
    val event: Event,
    val contacts: List<Contact>,
    val tags: List<Tag>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventsListViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val eventDao: EventDao,
    private val contactDao: ContactDao,
    private val tagDao: TagDao,
    settingsRepository: SettingsRepository
) : ViewModel() {

    /** P1 preference: absolute vs. relative rendering of event timestamps. */
    val dateDisplay: StateFlow<DateDisplay> = settingsRepository.dateDisplay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DateDisplay.ABSOLUTE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** P2: query failure surface for the shared `ErrorState` composable. */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    /** Bumped by [retryLoad] to re-subscribe the query after a failure. */
    private val refreshTrigger = MutableStateFlow(0)

    /** P2: null until the first query emission — drives the shared `LoadingState`. */
    val events: StateFlow<List<EventDisplayItem>?> = combine(refreshTrigger, _searchQuery) { _, query -> query }
        .flatMapLatest { query ->
            val source = if (query.isBlank()) {
                eventRepository.getAllEvents()
            } else {
                eventRepository.searchEvents(query)
            }
            source.map { eventList ->
                eventList.map { event ->
                    val contactIds = eventDao.getContactIdsForEvent(event.id)
                    val tagIds = eventDao.getTagIdsForEvent(event.id)
                    val contacts = contactIds.mapNotNull { contactDao.getById(it) }
                    val tags = tagIds.mapNotNull { tagId ->
                        tagDao.getAllOnce().find { it.id == tagId }
                    }
                    EventDisplayItem(event = event, contacts = contacts, tags = tags)
                }
            }
        }
        .onEach { _loadError.value = null }
        .catch { e ->
            _loadError.value = e.message
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun retryLoad() {
        _loadError.value = null
        refreshTrigger.value = refreshTrigger.value + 1
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            eventRepository.deleteEvent(event)
        }
    }
}
