package com.idontremember.personalcrm.ui.addevent

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.ui.components.AutocompleteTextField
import com.idontremember.personalcrm.ui.components.ContactChip
import com.idontremember.personalcrm.ui.components.TagChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEventSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    autoFocus: Boolean = true,
    viewModel: AddEventViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val importFailedMessage = stringResource(R.string.pilot_import_failed)
    val contactsUnavailableMessage = stringResource(R.string.pilot_contacts_unavailable)
    val createContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val id = try {
                result.data?.data?.let { uri ->
                    val column = if ("raw_contacts" in uri.pathSegments) ContactsContract.RawContacts.CONTACT_ID else ContactsContract.Contacts._ID
                    context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use {
                        if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
                    }
                }
            } catch (_: Exception) { null }
            if (id != null) viewModel.importCreatedContact(id) { Toast.makeText(context, importFailedMessage, Toast.LENGTH_SHORT).show() }
            else Toast.makeText(context, importFailedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val noteText by viewModel.noteText.collectAsState()
    val selectedContactIds by viewModel.selectedContactIds.collectAsState()
    val selectedTagNames by viewModel.selectedTagNames.collectAsState()
    val eventDate by viewModel.eventDate.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val isFact by viewModel.isFact.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val tags by viewModel.tags.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val errorBringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(saveFailed) { if (saveFailed) errorBringIntoView.bringIntoView() }

    LaunchedEffect(Unit) { viewModel.reloadDraft() }

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.reset(clearDraft = false)
            onSaved()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!saving) { viewModel.close(); onDismiss() }
        },
        sheetState = sheetState,
        modifier = Modifier.testTag("add_event_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.title_add_event),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    viewModel.close()
                    onDismiss()
                }, enabled = !saving, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.setFact(false) }, modifier = Modifier.semantics { selected = !isFact }) { Text(stringResource(if (!isFact) R.string.pilot_event_selected else R.string.pilot_event)) }
                TextButton(onClick = { viewModel.setFact(true) }, modifier = Modifier.semantics { selected = isFact }) { Text(stringResource(if (isFact) R.string.pilot_fact_selected else R.string.pilot_fact)) }
                TextButton(onClick = { viewModel.discard(); onDismiss() }, enabled = !saving) { Text(stringResource(R.string.pilot_discard)) }
            }
            if (isFact) Text(stringResource(R.string.pilot_select_one), style = MaterialTheme.typography.bodySmall)

            // Note text field with autocomplete
            AutocompleteTextField(
                value = noteText,
                onValueChange = { viewModel.onNoteChanged(it) },
                contacts = viewModel.getAutocompleteContacts(),
                tags = viewModel.getAutocompleteTags(),
                onContactSelected = { viewModel.onContactSelected(it) },
                onTagSelected = { viewModel.onTagSelected(it) },
                autoFocus = autoFocus,
                enableTags = !isFact,
                onCreateContact = { name ->
                    try { createContact.launch(Intent(Intent.ACTION_INSERT).setType(ContactsContract.Contacts.CONTENT_TYPE).putExtra(ContactsContract.Intents.Insert.NAME, name)) }
                    catch (_: Exception) { Toast.makeText(context, contactsUnavailableMessage, Toast.LENGTH_SHORT).show() }
                }
            )

            // Show selected contacts and tags as chips
            if (selectedContactIds.isNotEmpty() || (!isFact && selectedTagNames.isNotEmpty())) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedContactIds.forEach { id ->
                        ContactChip(
                            name = contacts.find { it.id == id }?.displayName ?: stringResource(R.string.pilot_unavailable_contact),
                            onRemove = { viewModel.removeContact(id) }
                        )
                    }
                    (if (isFact) emptySet() else selectedTagNames).forEach { tagName ->
                        TagChip(
                            name = tagName,
                            onRemove = { viewModel.removeTag(tagName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date/time display and pickers
            if (!isFact) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = SimpleDateFormat(
                            stringResource(R.string.date_pattern_short),
                            Locale.getDefault()
                        ).format(Date(eventDate)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = SimpleDateFormat(
                            stringResource(R.string.time_pattern),
                            Locale.getDefault()
                        ).format(Date(eventDate)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_event_button"),
                enabled = noteText.text.isNotBlank() && !saving && (!isFact || selectedContactIds.size == 1)
            ) {
                Text(if (isFact) stringResource(R.string.pilot_save_fact) else stringResource(R.string.action_save_event), style = MaterialTheme.typography.labelLarge)
            }
            if (saveFailed) {
                Text(stringResource(R.string.event_save_error), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.bringIntoViewRequester(errorBringIntoView).testTag("event_save_error"))
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = eventDate
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onDateChanged(it) }
                    showDatePicker = false
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = eventDate }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(java.util.Calendar.MINUTE)
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.select_time_title)) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = java.util.Calendar.getInstance().apply {
                        timeInMillis = eventDate
                        set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(java.util.Calendar.MINUTE, timePickerState.minute)
                    }
                    viewModel.onDateChanged(cal.timeInMillis)
                    showTimePicker = false
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
