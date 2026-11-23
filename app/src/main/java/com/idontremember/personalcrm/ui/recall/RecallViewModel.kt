package com.idontremember.personalcrm.ui.recall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.search.RecallEntry
import com.idontremember.personalcrm.data.search.RecallKind
import com.idontremember.personalcrm.data.search.RecallSearch
import com.idontremember.personalcrm.data.search.SearchRepository
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecallViewModel @Inject constructor(
    repository: SearchRepository,
    settings: SettingsRepository,
    private val state: SavedStateHandle
) : ViewModel() {
    val dateDisplay = settings.dateDisplay.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DateDisplay.ABSOLUTE
    )
    val query = state.getStateFlow("recall_query", "")
    val kind = state.getStateFlow<String?>("recall_kind", null).map(RecallKind::fromWireOrNull)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            RecallKind.fromWireOrNull(state["recall_kind"])
        )
    private val retry = MutableStateFlow(0)
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error
    val entries: StateFlow<List<RecallEntry>?> = retry.flatMapLatest {
        repository.entries.onEach { _error.value = false }.catch {
            _error.value = true
            emit(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val results = combine(entries, query, kind) { values, q, k ->
        values?.let { withContext(Dispatchers.Default) { RecallSearch.find(it, q, kind = k) } }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val personExcerpts = combine(entries, query) { values, q ->
        withContext(Dispatchers.Default) { RecallSearch.personExcerpts(values.orEmpty(), q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val history = combine(entries, query, kind) { values, q, k ->
        values?.let { withContext(Dispatchers.Default) { RecallSearch.history(it, q, k) } }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun filter(kind: RecallKind?) {
        state["recall_kind"] = kind?.wireValue
    }
    fun search(query: String) {
        state["recall_query"] = query
    }
    fun retry() {
        _error.value = false
        retry.value++
    }
}
