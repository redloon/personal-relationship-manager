package com.idontremember.personalcrm.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.contacts.*
import com.idontremember.personalcrm.data.db.entity.canDelete
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PeopleFeedback {
    data class Deleted(val count: Int) : PeopleFeedback
    data object DeleteFailed : PeopleFeedback
    data object Protected : PeopleFeedback
    data object PreviewChanged : PeopleFeedback
    data object SortFailed : PeopleFeedback
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsListViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val settings: SettingsRepository,
    interactions: InteractionRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val searchQuery = savedState.getStateFlow("people_query", "")
    private val scope = savedState.getStateFlow("people_scope", PeopleListScope.ALL.name)
    private val _selection = MutableStateFlow(PeopleSelection(
        savedState["selecting_people"] ?: false, savedState.get<LongArray>("selected_people")?.toSet().orEmpty()))
    val selection: StateFlow<PeopleSelection> = _selection
    private val _bulkBusy = MutableStateFlow(false)
    val bulkBusy: StateFlow<Boolean> = _bulkBusy
    private val _bulkFailed = MutableStateFlow(false)
    val bulkFailed: StateFlow<Boolean> = _bulkFailed
    private var retryChange: VisibilityChange? = null
    private val changes = Channel<VisibilityChange>(Channel.BUFFERED)
    val visibilityChanges = changes.receiveAsFlow()
    private val messages = Channel<PeopleFeedback>(Channel.BUFFERED)
    val feedback = messages.receiveAsFlow()
    private val _deletion = MutableStateFlow<PeopleDeletionPreview?>(null)
    val deletion: StateFlow<PeopleDeletionPreview?> = _deletion
    data class VisibilityChange(val ids: Set<Long>, val hidden: Boolean)

    fun configure(value: PeopleListScope) { savedState["people_scope"] = value.name }
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
    fun runAction(action: PeopleBulkAction) {
        when (action) {
            PeopleBulkAction.HIDE -> changeVisibility(selection.value.ids, true)
            PeopleBulkAction.UNHIDE -> changeVisibility(selection.value.ids, false)
            PeopleBulkAction.DELETE -> requestDeletion()
        }
    }
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

    private val availablePeople = scope.flatMapLatest { name ->
        when (PeopleListScope.valueOf(name)) {
            PeopleListScope.ALL -> contactRepository.getAllContacts()
            PeopleListScope.HIDDEN -> contactRepository.getHiddenContacts()
            PeopleListScope.APP_ONLY -> contactRepository.getAppOnlyContacts()
        }
    }.onEach { people -> setSelection(_selection.value.reconcile(people.map { it.id }.toSet())) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val canDeleteSelection = combine(availablePeople, selection) { people, selected ->
        val matching = people.filter { it.id in selected.ids }
        matching.isNotEmpty() && matching.size == selected.ids.size && matching.all { it.canDelete }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val sort = scope.flatMapLatest { settings.peopleSort(PeopleListScope.valueOf(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeopleSort.RECENT)
    fun changeSort(value: PeopleSort) {
        val page = PeopleListScope.valueOf(scope.value)
        viewModelScope.launch {
            try { settings.setPeopleSort(page, value) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { messages.send(PeopleFeedback.SortFailed) }
        }
    }
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError
    private val refreshTrigger = MutableStateFlow(0)
    val contacts: StateFlow<List<PeopleListItem>?> = refreshTrigger.flatMapLatest {
        combine(availablePeople, searchQuery, sort, interactions.snapshots) { people, query, order, snapshot ->
            withContext(Dispatchers.Default) {
                order.apply(ContactDirectorySearch.find(people, query).map { PeopleListItem(it, snapshot.forPerson(it.id), snapshot.asOf) })
            }
        }.onEach { _loadError.value = null }.catch { e ->
            if (e is CancellationException) throw e
            _loadError.value = e.message ?: "People unavailable"
            emit(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun onSearchQueryChanged(query: String) { savedState["people_query"] = query }
    fun retryLoad() { _loadError.value = null; refreshTrigger.value++ }

    private fun requestDeletion() {
        val ids = selection.value.ids
        if (ids.isEmpty() || _bulkBusy.value) return
        _bulkBusy.value = true
        viewModelScope.launch {
            try { _deletion.value = contactRepository.previewPeopleDeletion(ids) }
            catch (e: CancellationException) { throw e }
            catch (_: IllegalArgumentException) { messages.send(PeopleFeedback.Protected) }
            catch (_: Exception) { messages.send(PeopleFeedback.DeleteFailed) }
            finally { _bulkBusy.value = false }
        }
    }
    fun cancelDeletion() { if (!_bulkBusy.value) _deletion.value = null }
    fun confirmDeletion() {
        val preview = _deletion.value ?: return
        if (_bulkBusy.value) return
        _bulkBusy.value = true
        viewModelScope.launch {
            try {
                if (contactRepository.deletePeople(preview)) {
                    _deletion.value = null
                    setSelection(PeopleSelection())
                    messages.send(PeopleFeedback.Deleted(preview.people.size))
                } else {
                    _deletion.value = contactRepository.previewPeopleDeletion(preview.ids)
                    messages.send(PeopleFeedback.PreviewChanged)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: IllegalArgumentException) { _deletion.value = null; messages.send(PeopleFeedback.Protected) }
            catch (_: Exception) { _deletion.value = null; messages.send(PeopleFeedback.DeleteFailed) }
            finally { _bulkBusy.value = false }
        }
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState
    fun syncContacts() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try { _syncState.value = SyncState.Success(contactRepository.syncContacts()) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _syncState.value = SyncState.Error(e.message) }
        }
    }
    fun clearSyncState() { _syncState.value = SyncState.Idle }
    sealed class SyncState {
        data object Idle : SyncState()
        data object Syncing : SyncState()
        data class Success(val result: ContactsSyncManager.SyncResult) : SyncState()
        data class Error(val message: String?) : SyncState()
    }
}
