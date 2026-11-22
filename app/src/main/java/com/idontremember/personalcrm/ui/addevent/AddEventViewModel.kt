package com.idontremember.personalcrm.ui.addevent

import com.idontremember.personalcrm.data.capture.CaptureLinks
import com.idontremember.personalcrm.data.capture.InlineToken
import com.idontremember.personalcrm.data.capture.InlineTokens
import com.idontremember.personalcrm.data.capture.TokenTarget
import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.db.dao.ContactFactDao
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.repository.EventRepository
import com.idontremember.personalcrm.data.repository.TagRepository
import com.idontremember.personalcrm.data.settings.DefaultEventTime
import com.idontremember.personalcrm.data.settings.SettingsRepository
import com.idontremember.personalcrm.ui.components.AutocompleteContact
import com.idontremember.personalcrm.ui.components.AutocompleteTag
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class AddEventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val contactRepository: ContactRepository,
    private val tagRepository: TagRepository,
    private val settingsRepository: SettingsRepository,
    private val factDao: ContactFactDao,
    @ApplicationContext context: Context
) : ViewModel() {
    private val draftPrefs = context.getSharedPreferences("capture_draft", Context.MODE_PRIVATE)
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

    fun initialize(eventId: Long? = null, factId: Long? = null, contactId: Long? = null, sharedText: String? = null, initialIsFact: Boolean = false) {
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
                val stored = draftPrefs.getString(draftKey, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
                if (stored != null) {
                    readDraft(stored)
                    _resumingDraft.value = true
                    if (pendingSharedText != null || (eventId == null && factId == null &&
                        ((contactId != null && _selectedContactIds.value != setOf(contactId)) ||
                        ((contactId != null || initialIsFact) && _isFact.value != initialIsFact)))) _sharedDraftConflict.value = true
                } else if (eventId != null) {
                    val meta = eventRepository.getEventWithMeta(eventId) ?: error("Note unavailable")
                    _noteText.value = meta.event.note.asTextField()
                    _inlineTokens.value = InlineTokens.decode(meta.event.inlineTokens)
                    _selectedContactIds.value = meta.contacts.map { it.id }.toSet()
                    _selectedTagNames.value = meta.tags.map { it.name }.toSet()
                    _eventDate.value = meta.event.eventDate
                    _isFact.value = false
                } else if (factId != null) {
                    val fact = factDao.getById(factId) ?: error("Fact unavailable")
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
                acceptingChanges = true
                _ready.value = true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _saveFailed.value = true }
        }
    }

    fun reloadDraft() = initialize()

    fun resolveSharedDraft(resume: Boolean) {
        val incoming = pendingSharedText.orEmpty()
        if (!resume) {
            acceptingChanges = false
            _ready.value = false
            loadJob = viewModelScope.launch {
                freshCapture(pendingContactId, incoming)
                _isFact.value = pendingIsFact
                persistDraft()
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
            DefaultEventTime.LAST_USED -> settingsRepository.lastUsedEventTimestamp.first().takeIf { it > 0 } ?: System.currentTimeMillis()
        }
    }

    private fun readDraft(value: JSONObject) {
        _noteText.value = value.optString("note", "").asTextField()
        _selectedContactIds.value = value.optJSONArray("contacts").toLongSet()
        _selectedTagNames.value = value.optJSONArray("tags").toStringSet()
        _eventDate.value = value.optLong("date", System.currentTimeMillis())
        _isFact.value = value.optBoolean("fact", existingFactId != null)
        _inlineTokens.value = runCatching {
            InlineTokens.decode(value.optString("tokens", "[]")).also { InlineTokens.validate(_noteText.value.text, it, _selectedContactIds.value, _selectedTagNames.value) }
        }.getOrDefault(emptyList())
    }

    private inline fun change(block: () -> Unit) {
        if (_saving.value || !acceptingChanges || _sharedDraftConflict.value) return
        block()
        _saveFailed.value = false
        persistDraft()
    }
    private fun currentLinks() = CaptureLinks(_isFact.value, _selectedContactIds.value, _selectedTagNames.value, _inlineTokens.value)
    private fun applyLinks(links: CaptureLinks) {
        _isFact.value = links.isFact
        _selectedContactIds.value = links.contactIds
        _selectedTagNames.value = links.tagNames
        _inlineTokens.value = links.tokens
    }
    fun onNoteChanged(value: TextFieldValue) = change {
        applyLinks(currentLinks().edit(_noteText.value.text, value.text, _noteText.value.selection.start, _noteText.value.selection.end, value.selection.start))
        _noteText.value = value
    }
    private fun unlinkRemovedTokens(next: List<InlineToken>) {
        for (target in InlineTokens.removedTargets(_inlineTokens.value, next)) when (target) {
            is TokenTarget.Person -> _selectedContactIds.value = _selectedContactIds.value - target.id
            is TokenTarget.Tag -> _selectedTagNames.value = _selectedTagNames.value - target.name
        }
    }
    fun insertPersonToken(person: AutocompleteContact, start: Int, end: Int) = change {
        val inserted = InlineTokens.insert(_noteText.value.text, start, end, "@${person.displayName}", TokenTarget.Person(person.id), _inlineTokens.value)
        unlinkRemovedTokens(inserted.tokens)
        _inlineTokens.value = inserted.tokens
        _noteText.value = TextFieldValue(inserted.text, TextRange(inserted.cursor))
        _selectedContactIds.value = _selectedContactIds.value + person.id
    }
    fun insertTagToken(tag: AutocompleteTag, start: Int, end: Int) = change {
        val name = tag.name.trim().removePrefix("#").lowercase(java.util.Locale.ROOT)
        val inserted = InlineTokens.insert(_noteText.value.text, start, end, "#$name", TokenTarget.Tag(name), _inlineTokens.value)
        unlinkRemovedTokens(inserted.tokens)
        _inlineTokens.value = inserted.tokens
        _noteText.value = TextFieldValue(inserted.text, TextRange(inserted.cursor))
        _selectedTagNames.value = _selectedTagNames.value + name
    }
    fun onContactSelected(contact: AutocompleteContact) = change {
        applyLinks(currentLinks().selectPerson(contact.id))
    }
    fun onTagSelected(tag: AutocompleteTag) = change {
        val name = tag.name.trim().removePrefix("#").lowercase()
        if (name.isNotBlank()) _selectedTagNames.value = _selectedTagNames.value + name
    }
    fun removeContact(id: Long) = change { _selectedContactIds.value = _selectedContactIds.value - id; _inlineTokens.value = _inlineTokens.value.filterNot { it.target == TokenTarget.Person(id) } }
    fun removeTag(name: String) = change { _selectedTagNames.value = _selectedTagNames.value - name; _inlineTokens.value = _inlineTokens.value.filterNot { it.target == TokenTarget.Tag(name) } }
    fun onDateChanged(date: Long) = change { _eventDate.value = date }
    fun setFact(value: Boolean) = change {
        if (existingEventId == null && existingFactId == null) applyLinks(currentLinks().withFactMode(value))
    }

    private fun persistDraft() {
        val value = JSONObject().put("note", _noteText.value.text)
            .put("contacts", JSONArray(_selectedContactIds.value.toList()))
            .put("tags", JSONArray(_selectedTagNames.value.toList()))
            .put("date", _eventDate.value).put("fact", _isFact.value).put("tokens", InlineTokens.encode(_inlineTokens.value))
        draftPrefs.edit().putString(draftKey, value.toString()).apply()
    }

    fun save() {
        val note = _noteText.value.text
        if (note.isBlank() || !acceptingChanges || !_ready.value || _sharedDraftConflict.value || _saving.value || _saved.value || (_isFact.value && _selectedContactIds.value.size != 1)) return
        _saving.value = true
        _saveFailed.value = false
        viewModelScope.launch {
            try {
                when {
                    existingFactId != null -> {
                        val fact = factDao.getById(existingFactId!!) ?: error("Fact unavailable")
                        factDao.update(fact.copy(text = note, contactId = _selectedContactIds.value.single()))
                    }
                    existingEventId != null -> {
                        check(eventRepository.getEventById(existingEventId!!) != null)
                        eventRepository.updateEvent(existingEventId!!, note, _selectedContactIds.value.toList(), _selectedTagNames.value.toList(), _eventDate.value, _inlineTokens.value)
                    }
                    _isFact.value -> contactRepository.addFact(_selectedContactIds.value.single(), note)
                    else -> eventRepository.createEvent(note, _selectedContactIds.value.toList(), _selectedTagNames.value.toList(), _eventDate.value, _inlineTokens.value)
                }
                acceptingChanges = false
                draftPrefs.edit().remove(draftKey).apply()
                _saved.value = true
                try { if (!_isFact.value) settingsRepository.setLastUsedEventTimestamp(_eventDate.value) }
                catch (_: Exception) { /* Save has already committed. */ }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _saveFailed.value = true }
            finally { _saving.value = false }
        }
    }

    fun getAutocompleteContacts() = contacts.value.map { AutocompleteContact(it.id, it.displayName, it.phoneNumber) }
    fun getAutocompleteTags() = tags.value.map { AutocompleteTag(it.id, it.name) }
    fun close() { acceptingChanges = false; loadJob?.cancel() }
    fun discard() { close(); reset() }
    fun reset(clearDraft: Boolean = true) {
        if (clearDraft) draftPrefs.edit().remove(draftKey).apply()
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
private fun JSONArray?.toLongSet(): Set<Long> = if (this == null) emptySet() else (0 until length()).mapNotNull { optLong(it).takeIf { id -> id > 0 } }.toSet()
private fun JSONArray?.toStringSet(): Set<String> = if (this == null) emptySet() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }.toSet()
