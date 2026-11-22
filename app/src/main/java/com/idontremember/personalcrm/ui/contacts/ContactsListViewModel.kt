package com.idontremember.personalcrm.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.contacts.ContactsSyncManager
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.search.SearchRepository
import com.idontremember.personalcrm.data.search.RecallSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val contactRepository: ContactRepository,
    searchRepository: SearchRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {

    val searchQuery = savedState.getStateFlow("people_query", "")
    private val hiddenMode = savedState.getStateFlow("hidden_people", false)
    private val _selection = MutableStateFlow(PeopleSelection(
        savedState["selecting_people"] ?: false,
        savedState.get<LongArray>("selected_people")?.toSet().orEmpty()
    ))
    val selection: StateFlow<PeopleSelection> = _selection
    private val _bulkBusy = MutableStateFlow(false)
    val bulkBusy: StateFlow<Boolean> = _bulkBusy
    private val _bulkFailed = MutableStateFlow(false)
    val bulkFailed: StateFlow<Boolean> = _bulkFailed
    private var retryChange: VisibilityChange? = null
    private val changes = Channel<VisibilityChange>(Channel.BUFFERED)
    val visibilityChanges = changes.receiveAsFlow()

    data class VisibilityChange(val ids: Set<Long>, val hidden: Boolean)

    fun configure(hidden: Boolean) { savedState["hidden_people"] = hidden }

    private fun setSelection(value: PeopleSelection) {
        _selection.value = value
        savedState["selecting_people"] = value.active
        savedState["selected_people"] = value.ids.toLongArray()
    }

    fun startSelection() { if (!_bulkBusy.value) setSelection(PeopleSelection(active = true)) }
    fun clearSelection() { if (!_bulkBusy.value) setSelection(PeopleSelection()) }
    fun togglePerson(id: Long) { if (!_bulkBusy.value) setSelection(_selection.value.toggle(id)) }
    fun selectAllResults() {
        if (!_bulkBusy.value) setSelection(_selection.value.selectAll(contacts.value.orEmpty().map { it.id }.toSet()))
    }

    fun changeSelectedVisibility() = changeVisibility(_selection.value.ids, !hiddenMode.value)
    fun retryVisibilityChange() { retryChange?.let { changeVisibility(it.ids, it.hidden) } }

    fun changeVisibility(ids: Set<Long>, hidden: Boolean) {
        if (ids.isEmpty() || _bulkBusy.value) return
        _bulkBusy.value = true
        _bulkFailed.value = false
        retryChange = VisibilityChange(ids, hidden)
        viewModelScope.launch {
            try {
                val changed = contactRepository.setPeopleHidden(ids, hidden)
                setSelection(PeopleSelection())
                retryChange = null
                if (changed.isNotEmpty()) changes.send(VisibilityChange(changed, hidden))
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _bulkFailed.value = true }
            finally { _bulkBusy.value = false }
        }
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    /** P2: query failure surface for the shared `ErrorState` composable. */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    /** Bumped by [retryLoad] to re-subscribe the query after a failure. */
    private val refreshTrigger = MutableStateFlow(0)

    /** Filtering never changes the selection; only unavailable records prune it. */
    private val availablePeople = hiddenMode.flatMapLatest { hidden ->
        if (hidden) contactRepository.getHiddenContacts() else contactRepository.getAllContacts()
    }.onEach { people -> setSelection(_selection.value.reconcile(people.map { it.id }.toSet())) }

    val contacts: StateFlow<List<Contact>?> = refreshTrigger.flatMapLatest {
        combine(availablePeople, searchRepository.entries, searchQuery) { people, entries, query ->
            if (query.isBlank()) people else if (hiddenMode.value) {
                people.filter { person ->
                    listOfNotNull(person.displayName, person.phoneNumber, person.email)
                        .any { it.contains(query.trim(), ignoreCase = true) }
                }
            } else {
                val matches = withContext(Dispatchers.Default) { RecallSearch.find(entries, query) }
                val ids = matches.flatMap { it.personIds.trim(',').split(',').mapNotNull(String::toLongOrNull) }.toSet()
                people.filter { it.id in ids }
            }
        }.onEach { _loadError.value = null }.catch { e ->
            if (e is CancellationException) throw e
            _loadError.value = e.message ?: "People unavailable"
            emit(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val matchingExcerpts = combine(searchRepository.entries, searchQuery) { entries, query ->
        withContext(Dispatchers.Default) { RecallSearch.personExcerpts(entries, query) }
    }.catch { emit(emptyMap()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun onSearchQueryChanged(query: String) { savedState["people_query"] = query }

    fun syncContacts() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                val result = contactRepository.syncContacts()
                _syncState.value = SyncState.Success(result)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
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
