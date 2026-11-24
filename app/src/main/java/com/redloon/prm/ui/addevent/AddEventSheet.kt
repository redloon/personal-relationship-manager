package com.redloon.prm.ui.addevent

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.redloon.prm.R
import com.redloon.prm.data.capture.PersonChoice
import com.redloon.prm.data.capture.TagChoice
import com.redloon.prm.ui.components.AutocompleteTextField
import com.redloon.prm.ui.components.ContactChip
import com.redloon.prm.ui.components.ErrorState
import com.redloon.prm.ui.components.PersonPicker
import com.redloon.prm.ui.components.SearchField
import com.redloon.prm.ui.components.TagChip
import com.redloon.prm.ui.components.exposeTestTags
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Shared capture/edit surface. Save remains fixed while the editor scrolls above the IME. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEventSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    viewModel: AddEventViewModel = hiltViewModel(),
    existingEventId: Long? = null,
    existingFactId: Long? = null,
    initialContactId: Long? = null,
    initialText: String? = null,
    initialIsFact: Boolean = false
) {
    var suggestionWidth by remember { mutableStateOf(112.dp) }
    val noteText by viewModel.noteText.collectAsState()
    val inlineTokens by viewModel.inlineTokens.collectAsState()
    val resumingDraft by viewModel.resumingDraft.collectAsState()
    val selectedContactIds by viewModel.selectedContactIds.collectAsState()
    val selectedTagNames by viewModel.selectedTagNames.collectAsState()
    val eventDate by viewModel.eventDate.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val isFact by viewModel.isFact.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    val ready by viewModel.ready.collectAsState()
    val conflict by viewModel.sharedDraftConflict.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val selectedPeople by viewModel.selectedPeople.collectAsState()
    val tags by viewModel.tags.collectAsState()
    var showFollowUp by rememberSaveable { mutableStateOf(false) }
    var showPeople by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var pendingPersonQuery by rememberSaveable { mutableStateOf("") }
    var pendingPersonStart by rememberSaveable { mutableIntStateOf(-1) }
    var pendingPersonEnd by rememberSaveable { mutableIntStateOf(-1) }
    var tagQuery by rememberSaveable { mutableStateOf("") }
    val editing = existingEventId != null || existingFactId != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = with(LocalDensity.current) {
        (
            LocalWindowInfo.current.containerSize.height *
                0.92f
            ).toDp()
    }

    LaunchedEffect(existingEventId, existingFactId, initialContactId, initialText, initialIsFact) {
        viewModel.initialize(
            existingEventId,
            existingFactId,
            initialContactId,
            initialText,
            initialIsFact
        )
    }
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.reset(clearDraft = false)
            onSaved()
        }
    }
    val close = {
        if (!saving) {
            viewModel.close()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheetState,
        modifier = modifier.exposeTestTags().testTag("add_event_sheet")
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().heightIn(
                max = maxHeight
            ).padding(horizontal = 16.dp)
        ) {
            CaptureHeader(
                title = stringResource(
                    when {
                        existingFactId != null -> R.string.capture_edit_fact
                        editing -> R.string.capture_edit_note
                        resumingDraft && !conflict -> R.string.usability_resume_draft
                        isFact -> R.string.usability_add_fact
                        else -> R.string.capture_add_note
                    }
                ),
                saving = saving,
                saveEnabled =
                ready && noteText.text.isNotBlank() && !saving && !conflict &&
                    (!isFact || selectedContactIds.size == 1),
                editing = editing,
                onClose = close,
                onSave = viewModel::save,
                onSuggestionWidth = {
                    suggestionWidth =
                        it
                }
            )
            if (saveFailed && ready) {
                ErrorState(
                    message = stringResource(R.string.event_save_error),
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "event_save_error"
                )
            }
            if (!ready) {
                Text(
                    stringResource(
                        if (saveFailed) R.string.capture_unavailable else R.string.capture_loading
                    )
                )
            } else {
                if (!editing) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(selected = !isFact, onClick = {
                            viewModel.setFact(false)
                        }, label = {
                            Text(stringResource(R.string.capture_note), maxLines = 1)
                        }, enabled = !saving)
                        FilterChip(selected = isFact, onClick = {
                            viewModel.setFact(true)
                        }, label = {
                            Text(stringResource(R.string.pilot_fact), maxLines = 1)
                        }, enabled = !saving)
                        FilterChip(
                            selected = false,
                            onClick = {
                                showFollowUp = true
                            },
                            label = {
                                Text(stringResource(R.string.capture_follow_up), maxLines = 1)
                            },
                            enabled = !saving,
                            modifier = Modifier.testTag(
                                "capture_follow_up_button"
                            )
                        )
                    }
                }
                if (isFact &&
                    selectedContactIds.size != 1
                ) {
                    Text(
                        stringResource(R.string.usability_fact_person_required),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                AutocompleteTextField(
                    suggestionMaxWidth = suggestionWidth,
                    value = noteText, onValueChange = viewModel::onNoteChanged,
                    contacts = contacts.map {
                        PersonChoice(it.id, it.displayName, it.phoneNumber)
                    },
                    tags = viewModel.getAutocompleteTags(),
                    onContactSelected = viewModel::insertPersonToken,
                    onTagSelected = viewModel::insertTagToken,
                    autoFocus =
                    autoFocus && !conflict && !showPeople && !showTags && !showDate && !showTime,
                    enableTags = !isFact, enableContacts = !isFact,
                    inlineTokens = if (isFact) emptyList() else inlineTokens,
                    placeholder = if (isFact) {
                        stringResource(
                            R.string.usability_fact_prompt
                        )
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 56.dp),
                    onCreateContact = { query, start, end ->
                        pendingPersonQuery = query
                        pendingPersonStart =
                            start
                        pendingPersonEnd = end
                        showPeople = true
                    }
                )
                if (selectedContactIds.isNotEmpty() || (!isFact && selectedTagNames.isNotEmpty())) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!isFact &&
                            selectedContactIds.isEmpty()
                        ) {
                            TextButton(onClick = {
                                pendingPersonStart =
                                    -1
                                pendingPersonQuery = ""
                                showPeople = true
                            }, modifier = Modifier.testTag("capture_no_people")) {
                                Text(
                                    stringResource(R.string.usability_no_people_linked),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        selectedContactIds.forEach { id ->
                            ContactChip(
                                name =
                                selectedPeople.find { it.id == id }?.displayName
                                    ?: stringResource(R.string.pilot_unavailable_contact),
                                onRemove = { viewModel.removeContact(id) }
                            )
                        }
                        if (!isFact) {
                            selectedTagNames.forEach { name ->
                                TagChip(name = name, onRemove = { viewModel.removeTag(name) })
                            }
                        }
                    }
                } else if (!isFact) {
                    TextButton(onClick = {
                        pendingPersonStart = -1
                        pendingPersonQuery = ""
                        showPeople =
                            true
                    }, modifier = Modifier.testTag("capture_no_people")) {
                        Text(
                            stringResource(R.string.usability_no_people_linked),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = {
                        pendingPersonStart = -1
                        pendingPersonQuery = ""
                        showPeople =
                            true
                    }, enabled = !saving, modifier = Modifier.testTag("capture_people_button")) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = stringResource(R.string.capture_people)
                        )
                    }
                    if (!isFact) {
                        IconButton(onClick = {
                            showTags = true
                        }, enabled = !saving, modifier = Modifier.testTag("capture_tags_button")) {
                            Icon(
                                Icons.Default.Tag,
                                contentDescription = stringResource(R.string.capture_tags)
                            )
                        }
                        IconButton(onClick = {
                            showDate = true
                        }, enabled = !saving, modifier = Modifier.testTag("capture_date_button")) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription =
                                stringResource(R.string.capture_date) + ": " +
                                    SimpleDateFormat(
                                        stringResource(R.string.date_pattern_short),
                                        Locale.getDefault()
                                    ).format(Date(eventDate))
                            )
                        }
                        IconButton(onClick = {
                            showTime = true
                        }, enabled = !saving, modifier = Modifier.testTag("capture_time_button")) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription =
                                stringResource(R.string.select_time_title) + ": " +
                                    SimpleDateFormat(
                                        stringResource(R.string.time_pattern),
                                        Locale.getDefault()
                                    ).format(Date(eventDate))
                            )
                        }
                    }
                    IconButton(onClick = {
                        viewModel.discard()
                        onDismiss()
                    }, enabled = !saving, modifier = Modifier.testTag("capture_discard")) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.pilot_discard)
                        )
                    }
                }
            }
        }
    }

    if (showFollowUp) {
        com.redloon.prm.ui.followups.FollowUpEditor(
            contactId = selectedContactIds.singleOrNull(),
            initialText = noteText.text,
            onDismiss = { showFollowUp = false },
            onSaved = {
                viewModel.discard()
                onSaved()
            }
        )
    }

    if (conflict) {
        AlertDialog(
            modifier = Modifier.exposeTestTags(),
            onDismissRequest = close,
            title = {
                Text(stringResource(R.string.capture_shared_draft_title))
            },
            text = {
                Text(
                    stringResource(
                        if (initialText !=
                            null
                        ) {
                            R.string.capture_shared_draft_message
                        } else {
                            R.string.capture_context_draft_message
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resolveSharedDraft(true)
                }) { Text(stringResource(R.string.capture_resume_draft)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveSharedDraft(false) }) {
                    Text(
                        stringResource(
                            if (initialText !=
                                null
                            ) {
                                R.string.capture_start_shared
                            } else {
                                R.string.capture_start_context
                            }
                        )
                    )
                }
            }
        )
    }
    if (showPeople) {
        PersonPicker(
            onDismiss = {
                showPeople = false
                pendingPersonStart = -1
            },
            onPersonSelected = { person ->
                if (pendingPersonStart >=
                    0
                ) {
                    viewModel.insertPersonToken(person, pendingPersonStart, pendingPersonEnd)
                } else {
                    viewModel.onContactSelected(person)
                }
                pendingPersonStart = -1
                pendingPersonQuery = ""
                showPeople = false
            },
            selectedIds = if (isFact || pendingPersonStart >= 0) emptySet() else selectedContactIds,
            initialQuery = pendingPersonQuery,
            startCreating = pendingPersonStart >= 0
        )
    }
    if (showTags) {
        com.redloon.prm.ui.components.TagPicker(
            tags.filter { it.name !in selectedTagNames },
            tagQuery,
            { tagQuery = it },
            onDismiss = { showTags = false },
            onSelect = {
                viewModel.onTagSelected(TagChoice(it.id, it.name))
                tagQuery = ""
                showTags =
                    false
            },
            onCreate = {
                viewModel.onTagSelected(TagChoice(-1, it))
                tagQuery = ""
                showTags = false
            },
            capture = true
        )
    }
    if (showDate) {
        val calendar = Calendar.getInstance().apply { timeInMillis = eventDate }
        val utcDate = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
        }.timeInMillis
        val state = rememberDatePickerState(initialSelectedDateMillis = utcDate)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        viewModel.onDateChanged(withPickedDate(eventDate, it))
                    }
                    showDate =
                        false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDate = false
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state) }
    }
    if (showTime) {
        val calendar = Calendar.getInstance().apply { timeInMillis = eventDate }
        val state =
            rememberTimePickerState(
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)
            )
        AlertDialog(
            modifier = Modifier.exposeTestTags(),
            onDismissRequest = {
                showTime = false
            },
            title = {
                Text(stringResource(R.string.select_time_title))
            },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val changed = Calendar.getInstance().apply {
                        timeInMillis = eventDate
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                    }
                    viewModel.onDateChanged(changed.timeInMillis)
                    showTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTime = false
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
