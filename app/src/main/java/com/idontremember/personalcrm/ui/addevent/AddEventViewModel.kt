package com.idontremember.personalcrm.ui.addevent

import androidx.compose.ui.text.input.TextFieldValue
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val contacts: StateFlow<List<Contact>> = contactRepository
        .getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<Tag>> = tagRepository
        .getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _noteText = MutableStateFlow(TextFieldValue(""))
    val noteText: StateFlow<TextFieldValue> = _noteText

    private val _selectedContactIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedContactIds: StateFlow<Set<Long>> = _selectedContactIds

    private val _selectedTagNames = MutableStateFlow<Set<String>>(emptySet())
    val selectedTagNames: StateFlow<Set<String>> = _selectedTagNames

    private val _eventDate = MutableStateFlow(System.currentTimeMillis())
    val eventDate: StateFlow<Long> = _eventDate

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    init {
        // P1 "default event time" preference: now vs. the timestamp used last time.
        applyDefaultEventTime()
    }

    fun onNoteChanged(value: TextFieldValue) {
        _noteText.value = value
    }

    fun onContactSelected(contact: AutocompleteContact) {
        _selectedContactIds.value = _selectedContactIds.value + contact.id
    }

    fun onTagSelected(tag: AutocompleteTag) {
        _selectedTagNames.value = _selectedTagNames.value + tag.name
    }

    fun onDateChanged(dateMillis: Long) {
        _eventDate.value = dateMillis
    }

    fun save() {
        val note = _noteText.value.text
        if (note.isBlank()) return

        viewModelScope.launch {
            eventRepository.createEvent(
                note = note,
                contactIds = _selectedContactIds.value.toList(),
                tagNames = _selectedTagNames.value.toList(),
                eventDate = _eventDate.value
            )
            settingsRepository.setLastUsedEventTimestamp(_eventDate.value)
            _saved.value = true
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

    fun reset() {
        _noteText.value = TextFieldValue("")
        _selectedContactIds.value = emptySet()
        _selectedTagNames.value = emptySet()
        applyDefaultEventTime()
        _saved.value = false
    }

    private fun applyDefaultEventTime() {
        viewModelScope.launch {
            _eventDate.value = when (settingsRepository.defaultEventTime.first()) {
                DefaultEventTime.NOW -> System.currentTimeMillis()
                DefaultEventTime.LAST_USED -> {
                    val lastUsed = settingsRepository.lastUsedEventTimestamp.first()
                    if (lastUsed > 0L) lastUsed else System.currentTimeMillis()
                }
            }
        }
    }
}