package com.idontremember.personalcrm.ui.addevent

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.capture.CaptureDraft
import com.idontremember.personalcrm.data.capture.CaptureDraftStore
import com.idontremember.personalcrm.data.capture.CaptureLinks
import com.idontremember.personalcrm.data.capture.InlineToken
import com.idontremember.personalcrm.data.capture.InlineTokens
import com.idontremember.personalcrm.data.capture.PersonChoice
import com.idontremember.personalcrm.data.capture.TagChoice
import com.idontremember.personalcrm.data.capture.TokenTarget
import com.idontremember.personalcrm.data.db.dao.ContactFactDao
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.repository.EventRepository
import com.idontremember.personalcrm.data.repository.TagRepository
import com.idontremember.personalcrm.data.settings.DefaultEventTime
import com.idontremember.personalcrm.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AddEventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val contactRepository: ContactRepository,
    private val tagRepository: TagRepository,
    private val settingsRepository: SettingsRepository,
    private val factDao: ContactFactDao,
    private val drafts: CaptureDraftStore
) : ViewModel() {
    private var draftKey = "draft"
    private var existingEventId: Long? = null
    private var existingFactId: Long? = null
    private var acceptingChanges = false
    private var loadJob: Job? = null
    private var pendingSharedText: String? = null
    private var pendingContactId: Long? = null
    private var pendingIsFact = false

    val contacts = contactRepository.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Contact>())
    val tags = tagRepository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Tag>())

    private val _inlineTokens = MutableStateFlow<List<InlineToken>>(emptyList())
    val inlineTokens: StateFlow<List<InlineToken>> = _inlineTokens
    private val _resumingDraft = MutableStateFlow(false)
    val resumingDraft: StateFlow<Boolean> = _resumingDraft
    private val _noteText = MutableStateFlow(TextFieldValue(""))
    val noteText: StateFlow<TextFieldValue> = _noteText
    private val _selectedContactIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedContactIds: StateFlow<Set<Long>> = _selectedContactIds
    private val allPeople = contactRepository.getContactsIncludingHidden()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)
    val selectedPeople = combine(allPeople, selectedContactIds) { people, ids ->
        people.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _selectedTagNames = MutableStateFlow<Set<String>>(emptySet())
    val selectedTagNames: StateFlow<Set<String>> = _selectedTagNames
    private val _eventDate = MutableStateFlow(System.currentTimeMillis())
    val eventDate: StateFlow<Long> = _eventDate
    private val _isFact = MutableStateFlow(false)
    val isFact: StateFlow<Boolean> = _isFact
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val _sharedDraftConflict = MutableStateFlow(false)
    val sharedDraftConflict: StateFlow<Boolean> = _sharedDraftConflict

    init {
        viewModelScope.launch {
            allPeople.collect { people ->
                if (_ready.value && !_saving.value) reconcilePeople(people.map { it.id }.toSet())
            }
        }
    }
    private fun reconcilePeople(ids: Set<Long>) {
        val before = currentLinks()
        val after = before.reconcilePeople(ids)
        if (after != before) {
            applyLinks(after)
            persistDraft()
        }
    }

    fun initialize(
        eventId: Long? = null,
        factId: Long? = null,
        contactId: Long? = null,
        sharedText: String? = null,
        initialIsFact: Boolean = false
    ) {
        loadJob?.cancel()
        existingEventId = eventId
        existingFactId = factId
        draftKey = captureDraftKey(eventId, factId)
        acceptingChanges = false
        _ready.value = false
        _saved.value = false
        _saveFailed.value = false
        _saving.value = false
        _sharedDraftConflict.value = false
        _inlineTokens.value = emptyList()
        _resumingDraft.value = false
        pendingSharedText = sharedText?.takeIf { it.isNotBlank() }
        pendingContactId = contactId
        pendingIsFact = initialIsFact
        loadJob = viewModelScope.launch {
            try {
                val existingEvent = eventId?.let {
                    eventRepository.getEventWithMeta(it)
                        ?: error("Note unavailable")
                }
                val existingFact = factId?.let { factDao.getById(it) ?: error("Fact unavailable") }
                val stored = drafts.loadCapture(draftKey)
                if (stored != null) {
                    readDraft(stored)
                    _resumingDraft.value = true
                    if (pendingSharedText != null ||
                        (
                            eventId == null && factId == null &&
                                (
                                    (
                                        contactId != null &&
                                            _selectedContactIds.value != setOf(contactId)
                                        ) ||
                                        (
                                            (contactId != null || initialIsFact) &&
                                                _isFact.value != initialIsFact
                                            )
                                    )
                            )
                    ) {
                        _sharedDraftConflict.value =
                            true
                    }
                } else if (eventId != null) {
                    val meta = requireNotNull(existingEvent)
                    _noteText.value = meta.event.note.asTextField()
                    _inlineTokens.value = InlineTokens.decode(meta.event.inlineTokens)
                    _selectedContactIds.value = meta.contacts.map { it.id }.toSet()
                    _selectedTagNames.value = meta.tags.map { it.name }.toSet()
                    _eventDate.value = meta.event.eventDate
                    _isFact.value = false
                } else if (factId != null) {
                    val fact = requireNotNull(existingFact)
                    _noteText.value = fact.text.asTextField()
                    _selectedContactIds.value = setOf(fact.contactId)
                    _selectedTagNames.value = emptySet()
                    _eventDate.value = fact.createdAt
                    _isFact.value = true
                } else {
                    freshCapture(contactId, pendingSharedText.orEmpty())
                    _isFact.value = initialIsFact
                    if (!pendingSharedText.isNullOrBlank() || contactId != null) persistDraft()
                }
                reconcilePeople(allPeople.first().map { it.id }.toSet())
                acceptingChanges = true
                _ready.value = true
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                _saveFailed.value = true
            }
        }
    }

    fun resolveSharedDraft(resume: Boolean) {
        val incoming = pendingSharedText.orEmpty()
        if (!resume) {
            acceptingChanges = false
            _ready.value = false
            loadJob = viewModelScope.launch {
                freshCapture(pendingContactId, incoming)
                _isFact.value = pendingIsFact
                persistDraft()
                reconcilePeople(allPeople.first().map { it.id }.toSet())
                acceptingChanges = true
                _ready.value = true
            }
        }
        pendingSharedText = null
        _sharedDraftConflict.value = false
    }

    private suspend fun freshCapture(contactId: Long?, text: String) {
        _inlineTokens.value = emptyList()
        _resumingDraft.value = false
        _noteText.value = text.asTextField()
        _selectedContactIds.value = contactId?.let { setOf(it) }.orEmpty()
        _selectedTagNames.value = emptySet()
        _isFact.value = false
        _eventDate.value = when (settingsRepository.defaultEventTime.first()) {
            DefaultEventTime.NOW -> System.currentTimeMillis()
            DefaultEventTime.LAST_USED -> settingsRepository.lastUsedEventTimestamp.first().takeIf {
                it >
                    0
            }
                ?: System.currentTimeMillis()
        }
    }

    private fun readDraft(value: CaptureDraft) {
        _noteText.value = value.text.asTextField()
        applyLinks(value.links)
        _eventDate.value = value.eventDate
    }

    private inline fun change(block: () -> Unit) {
        if (_saving.value || !acceptingChanges || _sharedDraftConflict.value) return
        block()
        _saveFailed.value = false
        persistDraft()
    }
    private fun currentLinks() = CaptureLinks(
        _isFact.value,
        _selectedContactIds.value,
        _selectedTagNames.value,
        _inlineTokens.value
    )
    private fun applyLinks(links: CaptureLinks) {
        _isFact.value = links.isFact
        _selectedContactIds.value = links.contactIds
        _selectedTagNames.value = links.tagNames
        _inlineTokens.value = links.tokens
    }
    fun onNoteChanged(value: TextFieldValue) = change {
        applyLinks(
            currentLinks().edit(
                _noteText.value.text,
                value.text,
                _noteText.value.selection.start,
                _noteText.value.selection.end,
                value.selection.start
            )
        )
        _noteText.value = value
    }
    private fun insertToken(label: String, target: TokenTarget, start: Int, end: Int) {
        val inserted = currentLinks().insert(_noteText.value.text, start, end, label, target)
        applyLinks(inserted.links)
        _noteText.value = TextFieldValue(inserted.text, TextRange(inserted.cursor))
    }
    fun insertPersonToken(person: PersonChoice, start: Int, end: Int) = change {
        insertToken("@${person.displayName}", TokenTarget.Person(person.id), start, end)
    }
    fun insertTagToken(tag: TagChoice, start: Int, end: Int) = change {
        val name = tag.name.trim().removePrefix("#").lowercase(java.util.Locale.ROOT)
        insertToken("#$name", TokenTarget.Tag(name), start, end)
    }
    fun onContactSelected(contact: PersonChoice) = change {
        applyLinks(currentLinks().selectPerson(contact.id))
    }
    fun onTagSelected(tag: TagChoice) = change { applyLinks(currentLinks().selectTag(tag.name)) }
    fun removeContact(id: Long) = change { applyLinks(currentLinks().removePerson(id)) }
    fun removeTag(name: String) = change { applyLinks(currentLinks().removeTag(name)) }
    fun onDateChanged(date: Long) = change { _eventDate.value = date }
    fun setFact(value: Boolean) = change {
        if (existingEventId == null &&
            existingFactId == null
        ) {
            applyLinks(currentLinks().withFactMode(value))
        }
    }

    private fun persistDraft() {
        drafts.saveCapture(
            draftKey,
            CaptureDraft(_noteText.value.text, currentLinks(), _eventDate.value)
        )
    }

    fun save() {
        val note = _noteText.value.text
        if (note.isBlank() || !acceptingChanges || !_ready.value || _sharedDraftConflict.value ||
            _saving.value ||
            _saved.value ||
            (_isFact.value && _selectedContactIds.value.size != 1)
        ) {
            return
        }
        _saving.value = true
        _saveFailed.value = false
        viewModelScope.launch {
            try {
                when {
                    existingFactId != null -> {
                        val fact = factDao.getById(existingFactId!!) ?: error("Fact unavailable")
                        factDao.update(
                            fact.copy(text = note, contactId = _selectedContactIds.value.single())
                        )
                    }
                    existingEventId != null -> {
                        check(eventRepository.getEventById(existingEventId!!) != null)
                        eventRepository.updateEvent(
                            existingEventId!!,
                            note,
                            _selectedContactIds.value.toList(),
                            _selectedTagNames.value.toList(),
                            _eventDate.value,
                            _inlineTokens.value
                        )
                    }
                    _isFact.value -> contactRepository.addFact(
                        _selectedContactIds.value.single(),
                        note
                    )
                    else -> eventRepository.createEvent(
                        note,
                        _selectedContactIds.value.toList(),
                        _selectedTagNames.value.toList(),
                        _eventDate.value,
                        _inlineTokens.value
                    )
                }
                acceptingChanges = false
                drafts.remove(draftKey)
                _saved.value = true
                try {
                    if (!_isFact.value) {
                        settingsRepository.setLastUsedEventTimestamp(
                            _eventDate.value
                        )
                    }
                } catch (
                    _: Exception
                ) { /* Save has already committed. */ }
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (
                _: Exception
            ) {
                _saveFailed.value = true
            } finally {
                _saving.value = false
            }
        }
    }

    fun getAutocompleteTags() = tags.value.map { TagChoice(it.id, it.name) }
    fun close() {
        acceptingChanges = false
        loadJob?.cancel()
    }
    fun discard() {
        close()
        reset()
    }
    fun reset(clearDraft: Boolean = true) {
        if (clearDraft) drafts.remove(draftKey)
        _inlineTokens.value = emptyList()
        _resumingDraft.value = false
        _noteText.value = TextFieldValue("")
        _selectedContactIds.value = emptySet()
        _selectedTagNames.value = emptySet()
        _isFact.value = false
        _saved.value = false
        _saving.value = false
        _saveFailed.value = false
    }
}

private fun String.asTextField() = TextFieldValue(this, TextRange(length))
