package com.idontremember.personalcrm.ui.pilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.db.dao.PilotDao
import com.idontremember.personalcrm.data.db.entity.ContactTag
import com.idontremember.personalcrm.data.db.entity.Relationship
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PilotViewModel @Inject constructor(val dao: PilotDao, private val contactsRepo: ContactRepository, private val tagsRepo: TagRepository) : ViewModel() {
    val contacts = contactsRepo.getAllContacts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags = tagsRepo.getAllTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent = dao.recentContacts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun createTag(name: String) { if (name.isNotBlank()) viewModelScope.launch { tagsRepo.getOrCreateTag(name) } }
    fun deleteTag(tag: Tag) { viewModelScope.launch { tagsRepo.deleteTag(tag) } }
    fun assign(contact: Long, tag: Long) { viewModelScope.launch { dao.addTag(ContactTag(contact, tag)) } }
    fun unassign(contact: Long, tag: Long) { viewModelScope.launch { dao.removeTag(ContactTag(contact, tag)) } }
    fun link(a: Long, b: Long, label: String) { if (a != b && label.isNotBlank()) viewModelScope.launch { dao.addRelationship(Relationship(minOf(a,b), maxOf(a,b), label.trim())) } }
    fun unlink(link: Relationship) { viewModelScope.launch { dao.removeRelationship(link) } }
}
