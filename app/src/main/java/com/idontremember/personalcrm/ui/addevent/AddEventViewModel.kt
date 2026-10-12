package com.idontremember.personalcrm.ui.addevent

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.repository.EventRepository
import com.idontremember.personalcrm.data.repository.TagRepository
import com.idontremember.personalcrm.ui.components.AutocompleteContact
import com.idontremember.personalcrm.ui.components.AutocompleteTag
import com.idontremember.personalcrm.ui.components.TriggerType
import com.idontremember.personalcrm.ui.components.detectTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val contactRepository: ContactRepository,
    private val tagRepository: TagRepository
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

    fun onNoteChanged(value: TextFieldValue) {
        _noteText.value = value
        // Re-parse contacts and tags from text
        parseReferences(value.text)
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
            _saved.value = true
        }
    }

    /**
     * Parse @mentions and #tags from the text to track selected contacts/tags.
     * This handles the case where text is typed manually (not via autocomplete).
     */
    private fun parseReferences(text: String) {
        val contactNames = mutableSetOf<Long>()
        val tagNames = mutableSetOf<String>()

        // Find all @mentions
        val mentionRegex = Regex("""@(\w[\w\s]*?)(?=\s[#@\n]|$)""")
        mentionRegex.findAll(text).forEach { match ->
            val name = match.groupValues[1].trim()
            // Try to find matching contact
            contacts.value.find { it.displayName.equals(name, ignoreCase = true) }?.let {
                contactNames.add(it.id)
            }
        }

        // Find all #tags
        val tagRegex = Regex("""#(\w+)(?=\s[#@\n]|$)""")
        tagRegex.findAll(text).forEach { match ->
            tagNames.add(match.groupValues[1].lowercase())
        }

        // Merge with any manually selected via autocomplete
        _selectedContactIds.value = _selectedContactIds.value + contactNames
        _selectedTagNames.value = _selectedTagNames.value + tagNames
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
        _eventDate.value = System.currentTimeMillis()
        _saved.value = false
    }
}