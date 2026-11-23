package com.idontremember.personalcrm.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TagManagementViewModel @Inject constructor(private val repository: TagRepository) :
    ViewModel() {
    val tags = repository.getAllTags().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    fun peopleForTag(id: Long) = repository.peopleForTag(id)
    fun noteIdsForTag(id: Long) = repository.noteIdsForTag(id)
    fun createTag(name: String) {
        if (name.isNotBlank()) viewModelScope.launch { repository.getOrCreateTag(name) }
    }
    fun deleteTag(tag: Tag) {
        viewModelScope.launch { repository.deleteTag(tag) }
    }
}
