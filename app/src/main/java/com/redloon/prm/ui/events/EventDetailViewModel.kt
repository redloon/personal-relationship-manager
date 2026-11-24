package com.redloon.prm.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redloon.prm.data.db.dao.ContactDao
import com.redloon.prm.data.db.dao.EventDao
import com.redloon.prm.data.db.dao.TagDao
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.db.entity.Event
import com.redloon.prm.data.db.entity.Tag
import com.redloon.prm.data.recovery.RecoveryRepository
import com.redloon.prm.data.recovery.TrashKind
import com.redloon.prm.data.repository.EventRepository
import com.redloon.prm.data.settings.DateDisplay
import com.redloon.prm.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val eventDao: EventDao,
    private val contactDao: ContactDao,
    private val tagDao: TagDao,
    private val recovery: RecoveryRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    val dateDisplay = settingsRepository.dateDisplay.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DateDisplay.ABSOLUTE
    )
    private var eventId: Long = 0
    private var loadJob: Job? = null
    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event
    private val _eventLoaded = MutableStateFlow(false)
    val eventLoaded: StateFlow<Boolean> = _eventLoaded
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts
    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved
    private val _mutationFailed = MutableStateFlow(false)
    val mutationFailed: StateFlow<Boolean> = _mutationFailed

    fun setEventId(id: Long) {
        if (eventId == id) return
        eventId = id
        retryLoad()
    }

    fun retryLoad() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                combine(
                    eventDao.getByIdFlow(eventId),
                    contactDao.getAllIncludingHidden(),
                    tagDao.getAll()
                ) {
                        _,
                        _,
                        _
                    ->
                    eventRepository.getEventWithMeta(eventId)
                }
                    .collect { meta ->
                        if (!_saved.value) {
                            _event.value = meta?.event
                            _contacts.value = meta?.contacts.orEmpty()
                            _tags.value = meta?.tags.orEmpty()
                        }
                        _eventLoaded.value = true
                    }
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                _event.value = null
                _eventLoaded.value = true
            }
        }
    }

    fun deleteEvent() {
        if (_saved.value) return
        viewModelScope.launch {
            _mutationFailed.value = false
            _saved.value = true
            try {
                _event.value?.let { eventRepository.deleteEvent(it) }
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (
                _: Exception
            ) {
                _saved.value = false
                _mutationFailed.value = true
            }
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            _mutationFailed.value = false
            try {
                recovery.restore(TrashKind.EVENT, eventId)
                _saved.value = false
                retryLoad()
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                _mutationFailed.value = true
            }
        }
    }
}
