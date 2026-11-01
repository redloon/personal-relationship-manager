package com.idontremember.personalcrm.ui.addevent

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val contactRepository: ContactRepository,
    private val tagRepository: TagRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext context: Context
) : ViewModel() {

    private var acceptingChanges = true
    private val draftPrefs = context.getSharedPreferences("capture_draft", Context.MODE_PRIVATE)
    private val draft = runCatching { JSONObject(draftPrefs.getString("draft", null) ?: "{}") }.getOrDefault(JSONObject())

    val contacts: StateFlow<List<Contact>> = contactRepository
        .getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<Tag>> = tagRepository
        .getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _noteText = MutableStateFlow(draft.optString("note", "").let { TextFieldValue(it, selection = TextRange(it.length)) })
    val noteText: StateFlow<TextFieldValue> = _noteText

    private val _selectedContactIds = MutableStateFlow<Set<Long>>(draft.optJSONArray("contacts").toLongSet())
    val selectedContactIds: StateFlow<Set<Long>> = _selectedContactIds

    private val _selectedTagNames = MutableStateFlow<Set<String>>(draft.optJSONArray("tags").toStringSet())
    val selectedTagNames: StateFlow<Set<String>> = _selectedTagNames

    private val _eventDate = MutableStateFlow(draft.optLong("date", System.currentTimeMillis()))
    val eventDate: StateFlow<Long> = _eventDate

    private val _isFact = MutableStateFlow(draft.optBoolean("fact", false))
    val isFact: StateFlow<Boolean> = _isFact

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed

    init {
        // P1 "default event time" preference: now vs. the timestamp used last time.
        if (!draft.has("date")) applyDefaultEventTime()
    }

    fun reloadDraft() {
        acceptingChanges = true
        val stored = draftPrefs.getString("draft", null)
        if (stored == null) { reset(); return }
        val value = runCatching { JSONObject(stored) }.getOrElse { return }
        val note = value.optString("note", "")
        _noteText.value = TextFieldValue(note, selection = TextRange(note.length))
        _selectedContactIds.value = value.optJSONArray("contacts").toLongSet()
        _selectedTagNames.value = value.optJSONArray("tags").toStringSet()
        _eventDate.value = value.optLong("date", System.currentTimeMillis())
        _isFact.value = value.optBoolean("fact", false)
        _saved.value = false
        _saveFailed.value = false
    }

    fun onNoteChanged(value: TextFieldValue) {
        if (_saving.value || !acceptingChanges) return
        _noteText.value = value
        persistDraft()
    }

    fun onContactSelected(contact: AutocompleteContact) {
        if (_saving.value || !acceptingChanges) return
        _selectedContactIds.value = _selectedContactIds.value + contact.id
        persistDraft()
    }

    fun importCreatedContact(androidId: Long, onFailure: () -> Unit) {
        viewModelScope.launch {
            try {
                contactRepository.syncContacts()
                val contact = contactRepository.getContactByAndroidId(androidId) ?: error("Contact unavailable")
                _selectedContactIds.value = _selectedContactIds.value + contact.id
                persistDraft()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onFailure() }
        }
    }

    fun onTagSelected(tag: AutocompleteTag) {
        if (_saving.value || !acceptingChanges) return
        _selectedTagNames.value = _selectedTagNames.value + tag.name
        persistDraft()
    }

    fun removeContact(id: Long) {
        if (_saving.value || !acceptingChanges) return
        _selectedContactIds.value = _selectedContactIds.value - id
        persistDraft()
    }

    fun removeTag(name: String) {
        if (_saving.value || !acceptingChanges) return
        _selectedTagNames.value = _selectedTagNames.value - name
        persistDraft()
    }

    fun onDateChanged(dateMillis: Long) {
        if (_saving.value || !acceptingChanges) return
        _eventDate.value = dateMillis
        persistDraft()
    }

    fun setFact(value: Boolean) {
        if (_saving.value || !acceptingChanges) return
        _isFact.value = value
        persistDraft()
    }

    private fun persistDraft() {
        val value = JSONObject().put("note", _noteText.value.text)
            .put("contacts", JSONArray(_selectedContactIds.value.toList()))
            .put("tags", JSONArray(_selectedTagNames.value.toList()))
            .put("date", _eventDate.value).put("fact", _isFact.value)
        draftPrefs.edit().putString("draft", value.toString()).apply()
    }

    fun save() {
        val note = _noteText.value.text
        if (note.isBlank() || _saving.value || _saved.value || (_isFact.value && _selectedContactIds.value.size != 1)) return

        _saving.value = true
        _saveFailed.value = false
        viewModelScope.launch {
            try {
                if (_isFact.value) {
                    contactRepository.addFact(_selectedContactIds.value.single(), note)
                } else eventRepository.createEvent(
                    note = note,
                    contactIds = _selectedContactIds.value.toList(),
                    tagNames = _selectedTagNames.value.toList(),
                    eventDate = _eventDate.value
                )
                close()
                draftPrefs.edit().remove("draft").apply()
                _saved.value = true
                try { if (!_isFact.value) settingsRepository.setLastUsedEventTimestamp(_eventDate.value) }
                catch (_: Exception) { /* Event already saved. */ }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveFailed.value = true
            } finally {
                _saving.value = false
            }
        }
    }

    fun getAutocompleteContacts(): List<AutocompleteContact> {
        return contacts.value.map {
            AutocompleteContact(
                id = it.id,
                displayName = it.displayName,
                phoneNumber = it.phoneNumber
            )
        }
    }

    fun getAutocompleteTags(): List<AutocompleteTag> {
        return tags.value.map {
            AutocompleteTag(id = it.id, name = it.name)
        }
    }

    fun close() { acceptingChanges = false }

    fun discard() {
        close()
        reset()
    }

    fun reset(clearDraft: Boolean = true) {
        if (clearDraft) draftPrefs.edit().remove("draft").apply()
        _isFact.value = false
        _noteText.value = TextFieldValue("")
        _selectedContactIds.value = emptySet()
        _selectedTagNames.value = emptySet()
        applyDefaultEventTime()
        _saved.value = false
        _saving.value = false
        _saveFailed.value = false
    }

    private fun applyDefaultEventTime() {
        viewModelScope.launch {
            val defaultDate = when (settingsRepository.defaultEventTime.first()) {
                DefaultEventTime.NOW -> System.currentTimeMillis()
                DefaultEventTime.LAST_USED -> {
                    val lastUsed = settingsRepository.lastUsedEventTimestamp.first()
                    if (lastUsed > 0L) lastUsed else System.currentTimeMillis()
                }
            }
            if (!draftPrefs.contains("draft")) _eventDate.value = defaultDate
        }
    }
}

private fun JSONArray?.toLongSet(): Set<Long> = if (this == null) emptySet() else (0 until length()).map { optLong(it) }.toSet()
private fun JSONArray?.toStringSet(): Set<String> = if (this == null) emptySet() else (0 until length()).map { optString(it) }.toSet()
