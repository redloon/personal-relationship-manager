package com.redloon.prm.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redloon.prm.data.db.entity.Relationship
import com.redloon.prm.data.repository.ContactRepository
import com.redloon.prm.data.repository.ProfileLinksRepository
import com.redloon.prm.data.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileLinksViewModel @Inject constructor(
    private val repository: ProfileLinksRepository,
    contactsRepo: ContactRepository,
    tagsRepo: TagRepository
) : ViewModel() {
    val contacts = contactsRepo.getAllContacts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val linkedPeople = contactsRepo.getContactsIncludingHidden().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val tags = tagsRepo.getAllTags().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    fun assignedTags(id: Long) = repository.tagsForPerson(id)
    fun relationships(id: Long) = repository.relationshipsFor(id)
    fun assign(person: Long, tag: Long) {
        viewModelScope.launch { repository.assign(person, tag) }
    }
    fun unassign(person: Long, tag: Long) {
        viewModelScope.launch { repository.unassign(person, tag) }
    }
    fun link(a: Long, b: Long, label: String) {
        if (a != b &&
            label.isNotBlank()
        ) {
            viewModelScope.launch { repository.link(a, b, label) }
        }
    }
    fun unlink(link: Relationship) {
        viewModelScope.launch { repository.unlink(link) }
    }
}
