package com.idontremember.personalcrm.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.contacts.ContactsSyncManager
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsListViewModel @Inject constructor(
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    /** P2: query failure surface for the shared `ErrorState` composable. */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    /** Bumped by [retryLoad] to re-subscribe the query after a failure. */
    private val refreshTrigger = MutableStateFlow(0)

    /** P2: null until the first query emission — drives the shared `LoadingState`. */
    val contacts: StateFlow<List<Contact>?> = combine(refreshTrigger, _searchQuery) { _, query -> query }
        .flatMapLatest { query ->
            if (query.isBlank()) {
                contactRepository.getAllContacts()
            } else {
                contactRepository.searchContacts(query)
            }
        }
        .onEach { _loadError.value = null }
        .catch { e ->
            _loadError.value = e.message
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun syncContacts() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                val result = contactRepository.syncContacts()
                _syncState.value = SyncState.Success(result)
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message)
            }
        }
    }

    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun retryLoad() {
        _loadError.value = null
        refreshTrigger.value = refreshTrigger.value + 1
    }

    sealed class SyncState {
        data object Idle : SyncState()
        data object Syncing : SyncState()
        data class Success(val result: ContactsSyncManager.SyncResult) : SyncState()
        data class Error(val message: String?) : SyncState()
    }
}
