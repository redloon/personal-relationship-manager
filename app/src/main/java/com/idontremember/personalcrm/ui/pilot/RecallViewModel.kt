package com.idontremember.personalcrm.ui.pilot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.search.RecallEntry
import com.idontremember.personalcrm.data.search.RecallSearch
import com.idontremember.personalcrm.data.search.SearchRepository
import com.idontremember.personalcrm.data.settings.SettingsRepository
import com.idontremember.personalcrm.data.settings.DateDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecallViewModel @Inject constructor(repository: SearchRepository, settings: SettingsRepository, private val state: SavedStateHandle) : ViewModel() {
    val dateDisplay = settings.dateDisplay.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DateDisplay.ABSOLUTE)
    val query = state.getStateFlow("recall_query", "")
    private val retry = MutableStateFlow(0)
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error
    val entries: StateFlow<List<RecallEntry>?> = retry.flatMapLatest {
        repository.entries.onEach { _error.value = false }.catch { _error.value = true; emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val results = combine(entries, query) { values, q -> values?.let { withContext(Dispatchers.Default) { RecallSearch.find(it, q) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun search(query: String) { state["recall_query"] = query }
    fun retry() { _error.value = false; retry.value++ }
}
