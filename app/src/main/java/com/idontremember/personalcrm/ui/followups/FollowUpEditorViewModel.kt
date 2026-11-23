package com.idontremember.personalcrm.ui.followups

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.data.capture.CaptureDraftStore
import com.idontremember.personalcrm.data.capture.FollowUpDraft
import com.idontremember.personalcrm.data.capture.followUpDraftKey
import com.idontremember.personalcrm.data.followups.FollowUpRepository
import com.idontremember.personalcrm.data.followups.FollowUpWorker
import com.idontremember.personalcrm.data.recovery.RecoveryRepository
import com.idontremember.personalcrm.data.recovery.TrashKind
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FollowUpEditorError { SAVE, UNAVAILABLE }
data class FollowUpEditorState(
    val draft: FollowUpDraft = FollowUpDraft(),
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val conflict: Boolean = false,
    val completed: Boolean = false,
    val saved: Boolean = false,
    val askReminders: Boolean = false,
    val finished: Boolean = false,
    val deletedId: Long? = null,
    val error: FollowUpEditorError? = null
)

@HiltViewModel
class FollowUpEditorViewModel @Inject constructor(
    private val repository: FollowUpRepository,
    private val recovery: RecoveryRepository,
    private val settings: SettingsRepository,
    private val drafts: CaptureDraftStore,
    private val contacts: ContactRepository,
    private val savedState: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(FollowUpEditorState())
    val state: StateFlow<FollowUpEditorState> = _state
    private val allPeople = contacts.getContactsIncludingHidden().shareIn(
        viewModelScope,
        SharingStarted.Eagerly,
        replay = 1
    )
    val people = allPeople.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    private var activeKey: String? = null
    private var existingId: Long? = null
    private var incoming = FollowUpDraft()
    private var loading: Job? = null

    init {
        viewModelScope.launch {
            allPeople.collect { people ->
                val value = _state.value
                val person = value.draft.personId
                if (value.loaded && !value.busy && !value.saved && person != null &&
                    people.none { it.id == person }
                ) {
                    if (existingId != null && repository.get(existingId!!) == null) {
                        _state.value =
                            value.copy(loaded = false, error = FollowUpEditorError.UNAVAILABLE)
                    } else {
                        _state.value = value.copy(draft = value.draft.copy(personId = null))
                        persist()
                    }
                }
            }
        }
    }

    fun initialize(id: Long?, person: Long?, text: String?) {
        val key = followUpDraftKey(id, person)
        if (activeKey == key) return
        loading?.cancel()
        activeKey = key
        existingId = id
        incoming = FollowUpDraft(text.orEmpty(), person)
        val pending = savedState.get<String>("followup_pending_key") == key
        _state.value = FollowUpEditorState()
        loading = viewModelScope.launch {
            try {
                if (pending) {
                    val committed = repository.get(savedState.get<Long>("followup_committed_id")!!)
                        ?: error("Follow-up unavailable")
                    _state.value =
                        FollowUpEditorState(
                            FollowUpDraft(committed.text, committed.contactId, committed.dueDate),
                            loaded = true,
                            busy = true,
                            saved = true,
                            askReminders = true
                        )
                } else {
                    val stored = drafts.loadFollowUp(key)
                    val old = id?.let { repository.get(it) ?: error("Follow-up unavailable") }
                    val raw =
                        stored ?: old?.let { FollowUpDraft(it.text, it.contactId, it.dueDate) }
                            ?: incoming
                    val draft = if (raw.personId != null &&
                        contacts.getContactById(raw.personId) == null
                    ) {
                        raw.copy(personId = null)
                    } else {
                        raw
                    }
                    _state.value =
                        FollowUpEditorState(
                            draft,
                            loaded = true,
                            completed =
                            old?.completedAt != null,
                            conflict =
                            !text.isNullOrBlank() && stored != null && stored.text != text
                        )
                    persist()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(error = FollowUpEditorError.UNAVAILABLE)
            }
        }
    }

    private fun persist() {
        val value = _state.value
        if (value.loaded && !value.saved) activeKey?.let { drafts.saveFollowUp(it, value.draft) }
    }
    private fun edit(change: (FollowUpDraft) -> FollowUpDraft) {
        val value = _state.value
        if (!value.loaded || value.busy || value.conflict || value.saved) return
        _state.value = value.copy(draft = change(value.draft), error = null)
        persist()
    }
    fun text(value: String) = edit { it.copy(text = value) }
    fun person(value: Long) = edit { it.copy(personId = value) }
    fun due(value: String?) = edit { it.copy(dueDate = value) }
    fun resolveConflict(resume: Boolean) {
        _state.value =
            _state.value.copy(
                conflict = false,
                draft = if (resume) _state.value.draft else incoming
            )
        persist()
    }
    private fun action(block: suspend () -> Unit) {
        if (_state.value.busy || !_state.value.loaded || _state.value.conflict ||
            _state.value.saved
        ) {
            return
        }
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                block()
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(busy = false, error = FollowUpEditorError.SAVE)
            }
        }
    }
    fun save() = action {
        val draft = _state.value.draft
        val id = repository.save(
            existingId,
            requireNotNull(draft.personId),
            draft.text,
            draft.dueDate
        )
        activeKey?.let(drafts::remove)
        // Remember the committed ID before showing any optional permission UI.
        savedState["followup_pending_key"] = activeKey
        savedState["followup_committed_id"] = id
        _state.value = _state.value.copy(saved = true)
        val prompt = try {
            val needed =
                draft.dueDate != null && !settings.followUpRemindersEnabled.first() &&
                    !settings.followUpReminderPrompted.first()
            if (needed) settings.setFollowUpReminderPrompted()
            needed
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } // The record has already committed.
        _state.value = _state.value.copy(askReminders = prompt, finished = !prompt, busy = prompt)
    }
    fun complete() = action {
        val draft = _state.value.draft
        repository.saveAndComplete(
            requireNotNull(existingId),
            requireNotNull(draft.personId),
            draft.text,
            draft.dueDate,
            !_state.value.completed
        )
        activeKey?.let(drafts::remove)
        _state.value = _state.value.copy(finished = true, busy = false)
    }
    fun delete() = action {
        val id = requireNotNull(existingId)
        recovery.delete(TrashKind.FOLLOW_UP, id)
        activeKey?.let(drafts::remove)
        _state.value = _state.value.copy(deletedId = id)
    }
    fun finishDeletion(undo: Boolean) {
        val id = _state.value.deletedId ?: return
        viewModelScope.launch {
            try {
                if (undo) recovery.restore(TrashKind.FOLLOW_UP, id)
                _state.value = _state.value.copy(deletedId = null, finished = true, busy = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        deletedId = null,
                        busy = false,
                        error = FollowUpEditorError.SAVE
                    )
            }
        }
    }
    fun requestPermission() {
        _state.value = _state.value.copy(askReminders = false)
    }
    fun permissionResult(granted: Boolean) {
        viewModelScope.launch {
            try {
                settings.setFollowUpRemindersEnabled(granted)
                if (granted) FollowUpWorker.ensureScheduled(context)
                finishSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(askReminders = true, error = FollowUpEditorError.SAVE)
            }
        }
    }
    fun finishSaved() {
        _state.value =
            _state.value.copy(askReminders = false, finished = true, busy = false)
    }
    fun discard() {
        if (_state.value.busy) return
        activeKey?.let(drafts::remove)
        _state.value = _state.value.copy(finished = true)
    }
    fun consumeFinished() {
        _state.value = _state.value.copy(finished = false)
        savedState.remove<String>("followup_pending_key")
        savedState.remove<Long>("followup_committed_id")
        close()
    }
    fun close() {
        loading?.cancel()
        activeKey = null
    }
}
