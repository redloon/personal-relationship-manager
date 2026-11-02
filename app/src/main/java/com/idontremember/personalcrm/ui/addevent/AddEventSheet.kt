package com.idontremember.personalcrm.ui.addevent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.ui.components.exposeTestTags
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.ui.components.AutocompleteContact
import com.idontremember.personalcrm.ui.components.AutocompleteTag
import com.idontremember.personalcrm.ui.components.AutocompleteTextField
import com.idontremember.personalcrm.ui.components.ContactChip
import com.idontremember.personalcrm.ui.components.ErrorState
import com.idontremember.personalcrm.ui.components.SearchField
import com.idontremember.personalcrm.ui.components.TagChip
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
    autoFocus: Boolean = true,
    viewModel: AddEventViewModel = hiltViewModel(),
    existingEventId: Long? = null,
    existingFactId: Long? = null,
    initialContactId: Long? = null,
    initialText: String? = null,
    initialIsFact: Boolean = false
) {
    val noteText by viewModel.noteText.collectAsState()
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
    val tags by viewModel.tags.collectAsState()
    var showFollowUp by rememberSaveable { mutableStateOf(false) }
    var showPeople by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var personQuery by rememberSaveable { mutableStateOf("") }
    var tagQuery by rememberSaveable { mutableStateOf("") }
    val editing = existingEventId != null || existingFactId != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    LaunchedEffect(existingEventId, existingFactId, initialContactId, initialText, initialIsFact) {
        viewModel.initialize(existingEventId, existingFactId, initialContactId, initialText, initialIsFact)
    }
    LaunchedEffect(saved) { if (saved) { viewModel.reset(clearDraft = false); onSaved() } }
    val close = { if (!saving) { viewModel.close(); onDismiss() } }

    ModalBottomSheet(onDismissRequest = close, sheetState = sheetState, modifier = Modifier.exposeTestTags().testTag("add_event_sheet")) {
        Column(Modifier.fillMaxWidth().imePadding().heightIn(max = maxHeight).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(when {
                    existingFactId != null -> R.string.capture_edit_fact
                    editing -> R.string.capture_edit_note
                    else -> R.string.capture_add_note
                }), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = close, enabled = !saving) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                }
                Button(onClick = viewModel::save,
                    enabled = ready && noteText.text.isNotBlank() && !saving && !conflict && (!isFact || selectedContactIds.size == 1),
                    modifier = Modifier.testTag(if (editing) "save_event_edit_button" else "save_event_button")) {
                    Text(stringResource(if (saving) R.string.capture_saving else R.string.capture_save))
                }
            }
            if (saveFailed && ready) ErrorState(
                message = stringResource(R.string.event_save_error),
                modifier = Modifier.fillMaxWidth(),
                testTag = "event_save_error"
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!ready) {
                    Text(stringResource(if (saveFailed) R.string.capture_unavailable else R.string.capture_loading))
                } else {
                    if (!editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isFact, onClick = { viewModel.setFact(false) }, label = { Text(stringResource(R.string.capture_note), maxLines = 1) }, enabled = !saving)
                        FilterChip(selected = isFact, onClick = { viewModel.setFact(true) }, label = { Text(stringResource(R.string.pilot_fact), maxLines = 1) }, enabled = !saving)
                        FilterChip(selected = false, onClick = { showFollowUp = true }, label = { Text(stringResource(R.string.capture_follow_up), maxLines = 1) }, enabled = !saving, modifier = Modifier.testTag("capture_follow_up_button"))
                    }
                    if (isFact) Text(stringResource(R.string.pilot_select_one), style = MaterialTheme.typography.bodySmall)
                    AutocompleteTextField(
                        value = noteText, onValueChange = viewModel::onNoteChanged,
                        contacts = viewModel.getAutocompleteContacts(), tags = viewModel.getAutocompleteTags(),
                        onContactSelected = viewModel::onContactSelected, onTagSelected = viewModel::onTagSelected,
                        autoFocus = autoFocus && !conflict, enableTags = !isFact,
                        onCreateContact = { viewModel.createPerson(it) }
                    )
                    if (selectedContactIds.isNotEmpty() || (!isFact && selectedTagNames.isNotEmpty())) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            selectedContactIds.forEach { id -> ContactChip(
                                name = contacts.find { it.id == id }?.displayName ?: stringResource(R.string.pilot_unavailable_contact),
                                onRemove = { viewModel.removeContact(id) }
                            ) }
                            if (!isFact) selectedTagNames.forEach { name -> TagChip(name = name, onRemove = { viewModel.removeTag(name) }) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showPeople = true }, enabled = !saving, modifier = Modifier.testTag("capture_people_button")) { Text(stringResource(R.string.capture_people)) }
                        if (!isFact) {
                            TextButton(onClick = { showTags = true }, enabled = !saving, modifier = Modifier.testTag("capture_tags_button")) { Text(stringResource(R.string.capture_tags)) }
                            TextButton(onClick = { showDate = true }, enabled = !saving, modifier = Modifier.testTag("capture_date_button")) {
                                Text(SimpleDateFormat(stringResource(R.string.date_pattern_short), Locale.getDefault()).format(Date(eventDate)))
                            }
                            TextButton(onClick = { showTime = true }, enabled = !saving, modifier = Modifier.testTag("capture_time_button")) {
                                Text(SimpleDateFormat(stringResource(R.string.time_pattern), Locale.getDefault()).format(Date(eventDate)))
                            }
                        }
                    }
                    TextButton(onClick = { viewModel.discard(); onDismiss() }, enabled = !saving, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.pilot_discard)) }
                }
            }
        }
    }

    if (showFollowUp) com.idontremember.personalcrm.ui.followups.FollowUpEditor(
        contactId = selectedContactIds.singleOrNull(), initialText = noteText.text,
        onDismiss = { showFollowUp = false },
        onSaved = { viewModel.discard(); onSaved() }
    )

    if (conflict) AlertDialog(modifier = Modifier.exposeTestTags(),
        onDismissRequest = close, title = { Text(stringResource(R.string.capture_shared_draft_title)) },
        text = { Text(stringResource(if (initialText != null) R.string.capture_shared_draft_message else R.string.capture_context_draft_message)) },
        confirmButton = { TextButton(onClick = { viewModel.resolveSharedDraft(true) }) { Text(stringResource(R.string.capture_resume_draft)) } },
        dismissButton = { TextButton(onClick = { viewModel.resolveSharedDraft(false) }) { Text(stringResource(if (initialText != null) R.string.capture_start_shared else R.string.capture_start_context)) } }
    )
    if (showPeople) AlertDialog(modifier = Modifier.exposeTestTags(),
        onDismissRequest = { showPeople = false }, title = { Text(stringResource(R.string.capture_people)) },
        text = {
            Column {
                SearchField(personQuery, { personQuery = it }, stringResource(R.string.capture_people_hint), "clear_capture_person_search", Modifier.fillMaxWidth().testTag("event_person_search"))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(contacts.filter { it.displayName.contains(personQuery.trim(), true) && it.id !in selectedContactIds }, key = { it.id }) { person ->
                        TextButton(onClick = { viewModel.onContactSelected(AutocompleteContact(person.id, person.displayName)); personQuery = ""; showPeople = false }, modifier = Modifier.fillMaxWidth().testTag("event_person_option_${person.id}")) { Text(person.displayName) }
                    }
                }
                if (contacts.isEmpty() && personQuery.isBlank()) Text(stringResource(R.string.capture_empty_people))
                if (personQuery.isNotBlank()) TextButton(onClick = {
                    viewModel.createPerson(personQuery) { personQuery = ""; showPeople = false }
                }, modifier = Modifier.testTag("capture_create_person")) { Text(stringResource(R.string.capture_create_person, personQuery.trim())) }
            }
        }, confirmButton = { TextButton(onClick = { showPeople = false }) { Text(stringResource(R.string.pilot_done)) } }
    )
    if (showTags) AlertDialog(modifier = Modifier.exposeTestTags(),
        onDismissRequest = { showTags = false }, title = { Text(stringResource(R.string.capture_tags)) },
        text = {
            Column {
                SearchField(tagQuery, { tagQuery = it }, stringResource(R.string.capture_tag_hint), "clear_capture_tag_search", Modifier.fillMaxWidth().testTag("capture_tag_search"))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(tags.filter { it.name.contains(tagQuery.trim(), true) && it.name !in selectedTagNames }, key = { it.id }) { tag ->
                        TextButton(onClick = { viewModel.onTagSelected(AutocompleteTag(tag.id, tag.name)); tagQuery = ""; showTags = false }, modifier = Modifier.fillMaxWidth()) { Text(tag.name) }
                    }
                }
                if (tagQuery.trim().removePrefix("#").isNotBlank()) TextButton(onClick = {
                    viewModel.onTagSelected(AutocompleteTag(-1, tagQuery.trim().removePrefix("#").lowercase()))
                    tagQuery = ""; showTags = false
                }) { Text(stringResource(R.string.capture_create_tag, tagQuery.trim().removePrefix("#"))) }
            }
        }, confirmButton = { TextButton(onClick = { showTags = false }) { Text(stringResource(R.string.pilot_done)) } }
    )
    if (showDate) {
        val calendar = Calendar.getInstance().apply { timeInMillis = eventDate }
        val utcDate = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear(); set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
        val state = rememberDatePickerState(initialSelectedDateMillis = utcDate)
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { viewModel.onDateChanged(withPickedDate(eventDate, it)) }; showDate = false }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state) }
    }
    if (showTime) {
        val calendar = Calendar.getInstance().apply { timeInMillis = eventDate }
        val state = rememberTimePickerState(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
        AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { showTime = false }, title = { Text(stringResource(R.string.select_time_title)) }, text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = {
                val changed = Calendar.getInstance().apply { timeInMillis = eventDate; set(Calendar.HOUR_OF_DAY, state.hour); set(Calendar.MINUTE, state.minute) }
                viewModel.onDateChanged(changed.timeInMillis); showTime = false
            }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
