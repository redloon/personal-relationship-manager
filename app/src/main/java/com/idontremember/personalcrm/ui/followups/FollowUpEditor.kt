package com.idontremember.personalcrm.ui.followups

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.ui.components.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** UI owns dialogs and Android permissions; the editor owns all draft and write transitions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FollowUpEditor(
    contactId: Long? = null, existingId: Long? = null, initialText: String? = null,
    onSaved: (() -> Unit)? = null, onDismiss: () -> Unit,
    vm: FollowUpEditorViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    var choosingPerson by remember { mutableStateOf(false) }
    var choosingDate by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbar = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.followup_deleted)
    val undo = stringResource(R.string.followup_undo)
    val close = { vm.close(); onDismiss() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), vm::permissionResult)
    LaunchedEffect(existingId, contactId, initialText) { vm.initialize(existingId, contactId, initialText) }
    LaunchedEffect(state.finished) {
        if (state.finished) {
            val saved = state.saved
            vm.consumeFinished()
            if (saved) onSaved?.invoke()
            onDismiss()
        }
    }
    LaunchedEffect(state.loaded, state.conflict, state.saved, sheet.currentValue) {
        if (state.loaded && !state.conflict && !state.saved && sheet.currentValue == SheetValue.Expanded) {
            if (existingId == null) focus.requestFocus() else keyboard?.hide()
        }
    }
    LaunchedEffect(state.deletedId) {
        if (state.deletedId != null) vm.finishDeletion(
            snackbar.showSnackbar(deleted, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
    }
    ModalBottomSheet(
        modifier = Modifier.exposeTestTags(), sheetState = sheet,
        onDismissRequest = { if (!state.busy) close() }
    ) {
        Column(Modifier.fillMaxHeight(.9f).imePadding().padding(horizontal = 16.dp).testTag("followup_editor")) {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(if (existingId == null) R.string.followup_add else R.string.followup_edit),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(top = 12.dp))
                IconButton(onClick = close, enabled = !state.busy) { Icon(Icons.Default.Close, stringResource(R.string.cd_close)) }
            }
            FlowRow {
                TextButton(onClick = { choosingPerson = true }, enabled = !state.busy, modifier = Modifier.testTag("followup_choose_person")) {
                    Text(people.find { it.id == state.draft.personId }?.displayName ?: stringResource(R.string.followup_person))
                }
                TextButton(onClick = { choosingDate = true }, enabled = !state.busy, modifier = Modifier.testTag("followup_choose_date")) {
                    Text(state.draft.dueDate?.let(::formatFollowUpDate) ?: stringResource(R.string.followup_date))
                }
                if (state.draft.dueDate != null) TextButton(onClick = { vm.due(null) }, enabled = !state.busy) {
                    Text(stringResource(R.string.followup_no_date))
                }
            }
            OutlinedTextField(state.draft.text, vm::text, label = { Text(stringResource(R.string.followup_hint)) }, minLines = 1,
                enabled = state.loaded && !state.busy && !state.conflict,
                modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focus).testTag("followup_text"))
            state.error?.let { error ->
                Text(stringResource(if (error == FollowUpEditorError.UNAVAILABLE) R.string.followup_unavailable else R.string.followup_error),
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("followup_error"))
            }
            if (existingId != null && state.loaded) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = vm::complete, enabled = !state.busy && state.draft.text.isNotBlank() && state.draft.personId != null) {
                    Text(stringResource(if (state.completed) R.string.followup_reopen else R.string.followup_done))
                }
                IconButton(onClick = vm::delete, enabled = !state.busy) { Icon(Icons.Default.Delete, stringResource(R.string.followup_delete)) }
            }
            SnackbarHost(snackbar)
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { confirmDiscard = true }, enabled = !state.busy) { Text(stringResource(R.string.pilot_discard)) }
                Button(onClick = vm::save,
                    enabled = state.loaded && !state.busy && !state.saved && !state.conflict && state.draft.text.isNotBlank() && state.draft.personId != null,
                    modifier = Modifier.testTag("save_followup")) { Text(stringResource(R.string.followup_save)) }
            }
        }
    }
    if (state.conflict) AlertDialog(
        modifier = Modifier.exposeTestTags(), onDismissRequest = {},
        title = { Text(stringResource(R.string.followup_existing_draft)) },
        text = { Text(stringResource(R.string.followup_existing_draft_detail)) },
        confirmButton = { TextButton(onClick = { vm.resolveConflict(true) }) { Text(stringResource(R.string.capture_resume_draft)) } },
        dismissButton = { TextButton(onClick = { vm.resolveConflict(false) }) { Text(stringResource(R.string.followup_replace_draft)) } }
    )
    if (confirmDiscard) AlertDialog(
        modifier = Modifier.exposeTestTags(), onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.followup_discard_title)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.discard() }) { Text(stringResource(R.string.pilot_discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.followup_cancel)) } }
    )
    if (choosingPerson) PersonPicker(onDismiss = { choosingPerson = false },
        onPersonSelected = { vm.person(it.id); choosingPerson = false })
    if (choosingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.draft.dueDate?.let {
            LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        })
        DatePickerDialog(
            modifier = Modifier.exposeTestTags(), onDismissRequest = { choosingDate = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let { vm.due(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                choosingDate = false
            }) { Text(stringResource(R.string.followup_save)) } },
            dismissButton = { TextButton(onClick = { choosingDate = false }) { Text(stringResource(R.string.followup_cancel)) } }
        ) { DatePicker(picker) }
    }
    if (state.askReminders) AlertDialog(
        modifier = Modifier.exposeTestTags(), onDismissRequest = vm::finishSaved,
        title = { Text(stringResource(R.string.followup_reminder_title)) },
        text = { Text(stringResource(R.string.followup_reminder_explanation)) },
        confirmButton = { TextButton(onClick = {
            vm.requestPermission()
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.permissionResult(true)
        }) { Text(stringResource(R.string.followup_enable)) } },
        dismissButton = { TextButton(onClick = vm::finishSaved) { Text(stringResource(R.string.followup_not_now)) } }
    )
}
