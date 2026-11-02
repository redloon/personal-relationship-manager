package com.idontremember.personalcrm.ui.followups

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idontremember.personalcrm.ui.components.exposeTestTags
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.followups.FollowUpWithPerson
import com.idontremember.personalcrm.data.followups.FollowUpWorker
import com.idontremember.personalcrm.data.recovery.TrashKind
import com.idontremember.personalcrm.ui.components.SearchField
import com.idontremember.personalcrm.ui.settings.SettingsSwitchRow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun FollowUpsSection(contactId: Long? = null, vm: FollowUpViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val related = items.filter { contactId == null || it.item.contactId == contactId }
    val pending = related.filter { it.item.completedAt == null }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    if (contactId != null || related.isNotEmpty()) Column(Modifier.fillMaxWidth().testTag("followups_section")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.followup_title), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { adding = true }) { Text(stringResource(R.string.followup_add)) }
        }
        pending.take(3).forEach { FollowUpRow(it, onClick = { editing = it.item.id }) }
        if (related.isNotEmpty()) TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.followup_all)) }
    }
    if (showAll) FollowUpsOverlay(contactId = contactId, onDismiss = { showAll = false })
    if (adding || editing != null) FollowUpEditor(contactId = contactId, existingId = editing, onDismiss = { adding = false; editing = null })
}

@Composable
private fun FollowUpRow(entry: FollowUpWithPerson, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(entry.item.text, maxLines = 3) },
        supportingContent = { Text(listOfNotNull(entry.name.takeIf { it.isNotBlank() }, entry.item.dueDate).joinToString(" · ")) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("followup_${entry.item.id}"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpsOverlay(contactId: Long? = null, onDismiss: () -> Unit, vm: FollowUpViewModel = hiltViewModel()) {
    val entries by vm.items.collectAsStateWithLifecycle()
    var completed by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    val visible = entries.filter { (contactId == null || it.item.contactId == contactId) && (it.item.completedAt != null) == completed }
    ModalBottomSheet(modifier = Modifier.exposeTestTags(), onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(.85f).padding(horizontal = 16.dp).testTag("followups_list")) {
            Text(stringResource(R.string.followup_title), style = MaterialTheme.typography.headlineSmall)
            Row {
                FilterChip(selected = !completed, onClick = { completed = false }, label = { Text(stringResource(R.string.followup_pending)) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = completed, onClick = { completed = true }, label = { Text(stringResource(R.string.followup_completed)) })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { adding = true }) { Text(stringResource(R.string.followup_add)) }
            }
            if (visible.isEmpty()) Text(stringResource(R.string.followup_empty), Modifier.padding(vertical = 24.dp))
            LazyColumn(Modifier.weight(1f)) { items(visible, key = { it.item.id }) { entry -> FollowUpRow(entry) { editing = entry.item.id } } }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.followup_close)) }
        }
    }
    if (adding || editing != null) FollowUpEditor(contactId = contactId, existingId = editing, onDismiss = { adding = false; editing = null })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FollowUpEditor(contactId: Long? = null, existingId: Long? = null, initialText: String? = null, onSaved: (() -> Unit)? = null, onDismiss: () -> Unit, vm: FollowUpViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("capture_draft", Context.MODE_PRIVATE) }
    val key = "followup_${existingId ?: contactId?.let { "person_$it" } ?: "new"}"
    val stored = remember(key) { runCatching { JSONObject(prefs.getString(key, null) ?: "{}") }.getOrDefault(JSONObject()) }
    var text by rememberSaveable(key) { mutableStateOf(stored.optString("text", initialText.orEmpty())) }
    var person by rememberSaveable(key) { mutableStateOf(if (stored.has("person")) stored.optLong("person") else contactId) }
    var due by rememberSaveable(key) { mutableStateOf(stored.optString("date", "").ifBlank { null }) }
    var draftConflict by remember(key) { mutableStateOf(!initialText.isNullOrBlank() && stored.has("text") && stored.optString("text") != initialText) }
    var loaded by remember(key) { mutableStateOf(existingId == null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var choosingPerson by remember { mutableStateOf(false) }
    var choosingDate by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var askReminders by rememberSaveable(key) { mutableStateOf(false) }
    var savedAndWaiting by rememberSaveable(key) { mutableStateOf(false) }
    val people by vm.people.collectAsStateWithLifecycle()
    val remindersEnabled by vm.reminders.collectAsStateWithLifecycle()
    val reminderPrompted by vm.prompted.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val expanded = editorSheetState.currentValue == SheetValue.Expanded
    val snackbar = remember { SnackbarHostState() }
    val saveError = stringResource(R.string.followup_error)
    val unavailable = stringResource(R.string.followup_unavailable)
    val permissionDenied = stringResource(R.string.followup_permission_denied)
    val deletedMessage = stringResource(R.string.followup_deleted)
    val undo = stringResource(R.string.followup_undo)
    val finishSaved = { onSaved?.invoke(); onDismiss() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            vm.settings.setFollowUpRemindersEnabled(granted)
            if (granted) FollowUpWorker.ensureScheduled(context)
            if (savedAndWaiting) finishSaved() else if (!granted) snackbar.showSnackbar(permissionDenied)
        }
    }
    LaunchedEffect(existingId) {
        if (existingId != null) {
            val existing = try { vm.repository.get(existingId) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
            if (existing == null) error = unavailable
            else {
                if (!stored.has("text")) { text = existing.text; person = existing.contactId; due = existing.dueDate }
                loaded = true
            }
        }
    }
    LaunchedEffect(loaded, draftConflict, expanded) {
        if (loaded && !draftConflict && !savedAndWaiting && expanded) {
            if (existingId == null) focusRequester.requestFocus() else keyboard?.hide()
        }
    }
    LaunchedEffect(text, person, due, loaded) {
        if (loaded && !savedAndWaiting) {
            val value = JSONObject().put("text", text).put("date", due ?: "")
            person?.let { value.put("person", it) }
            prefs.edit().putString(key, value.toString()).apply()
        }
    }
    fun enableReminders() {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else scope.launch { vm.settings.setFollowUpRemindersEnabled(true); FollowUpWorker.ensureScheduled(context); if (savedAndWaiting) finishSaved() }
    }
    ModalBottomSheet(modifier = Modifier.exposeTestTags(), onDismissRequest = { if (!saving) { if (savedAndWaiting) finishSaved() else onDismiss() } }, sheetState = editorSheetState) {
        Column(Modifier.fillMaxHeight(.9f).imePadding().padding(horizontal = 16.dp).testTag("followup_editor")) {
            Text(stringResource(if (existingId == null) R.string.followup_add else R.string.followup_edit), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { choosingPerson = true }, enabled = !saving, modifier = Modifier.testTag("followup_choose_person")) {
                    Text(people.find { it.id == person }?.displayName ?: stringResource(R.string.followup_person))
                }
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.followup_hint)) }, minLines = 3,
                    enabled = !saving && loaded && !draftConflict, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("followup_text"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { choosingDate = true }, enabled = !saving, modifier = Modifier.testTag("followup_choose_date")) { Text(due ?: stringResource(R.string.followup_date)) }
                    if (due != null) TextButton(onClick = { due = null }, enabled = !saving) { Text(stringResource(R.string.followup_no_date)) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("followup_error")) }
                if (existingId != null && loaded) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !saving && text.isNotBlank() && person != null, onClick = {
                            saving = true
                            scope.launch {
                                try { val item = vm.repository.get(existingId); vm.repository.saveAndComplete(existingId, person!!, text, due, item?.completedAt == null); prefs.edit().remove(key).apply(); onDismiss() }
                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (_: Exception) { error = saveError; saving = false }
                            }
                        }) {
                            val entries by vm.items.collectAsStateWithLifecycle()
                            val done = entries.find { it.item.id == existingId }?.item?.completedAt != null
                            Text(stringResource(if (done) R.string.followup_reopen else R.string.followup_done))
                        }
                        TextButton(enabled = !saving, onClick = {
                            saving = true
                            scope.launch {
                                try {
                                    vm.recovery.delete(TrashKind.FOLLOW_UP, existingId)
                                    prefs.edit().remove(key).apply()
                                    if (snackbar.showSnackbar(deletedMessage, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                                        vm.recovery.restore(TrashKind.FOLLOW_UP, existingId)
                                    onDismiss()
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (_: Exception) { error = saveError; saving = false }
                            }
                        }) { Text(stringResource(R.string.followup_delete)) }
                    }
                }
            }
            SnackbarHost(snackbar)
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.followup_close)) }
                TextButton(onClick = { confirmDiscard = true }, enabled = !saving) { Text(stringResource(R.string.pilot_discard)) }
                Button(enabled = loaded && !saving && !savedAndWaiting && !draftConflict && text.isNotBlank() && person != null, modifier = Modifier.testTag("save_followup"), onClick = {
                    saving = true
                    scope.launch {
                        try {
                            vm.repository.save(existingId, person!!, text, due)
                            savedAndWaiting = true
                            prefs.edit().remove(key).apply()
                            if (due != null && !remindersEnabled && !reminderPrompted) {
                                vm.settings.setFollowUpReminderPrompted()
                                askReminders = true
                            } else finishSaved()
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { error = saveError; saving = false }
                    }
                }) { Text(stringResource(R.string.followup_save)) }
            }
        }
    }
    if (draftConflict) AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = {}, title = { Text(stringResource(R.string.followup_existing_draft)) },
        text = { Text(stringResource(R.string.followup_existing_draft_detail)) },
        confirmButton = { TextButton(onClick = { draftConflict = false }) { Text(stringResource(R.string.capture_resume_draft)) } },
        dismissButton = { TextButton(onClick = { text = initialText.orEmpty(); person = contactId; due = null; draftConflict = false }) { Text(stringResource(R.string.followup_replace_draft)) } })
    if (confirmDiscard) AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { confirmDiscard = false }, title = { Text(stringResource(R.string.followup_discard_title)) },
        confirmButton = { TextButton(onClick = { prefs.edit().remove(key).apply(); confirmDiscard = false; onDismiss() }) { Text(stringResource(R.string.pilot_discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.followup_cancel)) } })
    if (choosingPerson) AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { choosingPerson = false }, title = { Text(stringResource(R.string.followup_person)) }, text = {
        Column(Modifier.heightIn(max = 400.dp)) {
            SearchField(query, { query = it }, stringResource(R.string.followup_search), "followup_clear_person", Modifier.fillMaxWidth())
            LazyColumn { items(people.filter { it.displayName.contains(query, true) }, key = { it.id }) { candidate ->
                TextButton(onClick = { person = candidate.id; choosingPerson = false }, modifier = Modifier.fillMaxWidth().testTag("followup_person_${candidate.id}")) { Text(candidate.displayName) }
            } }
        }
    }, confirmButton = { TextButton(onClick = { choosingPerson = false }) { Text(stringResource(R.string.followup_close)) } })
    if (choosingDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = due?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() })
        DatePickerDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { choosingDate = false }, confirmButton = { TextButton(onClick = {
            dateState.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; choosingDate = false
        }) { Text(stringResource(R.string.followup_save)) } }, dismissButton = { TextButton(onClick = { choosingDate = false }) { Text(stringResource(R.string.followup_cancel)) } }) { DatePicker(state = dateState) }
    }
    if (askReminders) AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { askReminders = false; finishSaved() }, title = { Text(stringResource(R.string.followup_reminder_title)) }, text = { Text(stringResource(R.string.followup_reminder_explanation)) },
        confirmButton = { TextButton(onClick = { askReminders = false; enableReminders() }) { Text(stringResource(R.string.followup_enable)) } },
        dismissButton = { TextButton(onClick = { askReminders = false; finishSaved() }) { Text(stringResource(R.string.followup_not_now)) } })
}

@Composable
fun ReminderPreferences(vm: FollowUpViewModel = hiltViewModel()) {
    val enabled by vm.reminders.collectAsStateWithLifecycle()
    val hour by vm.hour.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        scope.launch { vm.settings.setFollowUpRemindersEnabled(granted); if (granted) FollowUpWorker.ensureScheduled(context) }
    }
    Column(Modifier.fillMaxWidth().testTag("reminder_settings")) {
        SettingsSwitchRow(
            title = stringResource(R.string.followup_reminders),
            checked = enabled,
            onCheckedChange = { value ->
                if (value && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else scope.launch { vm.settings.setFollowUpRemindersEnabled(value); if (value) FollowUpWorker.ensureScheduled(context) }
            },
            testTag = "followup_reminders_toggle"
        )
        Text(stringResource(R.string.followup_reminder_explanation), style = MaterialTheme.typography.bodySmall)
        if (enabled) Row {
            TextButton(onClick = { scope.launch { vm.settings.setReminderHour(hour - 1) } }, enabled = hour > 6) { Text(stringResource(R.string.followup_hour_earlier)) }
            Text(stringResource(R.string.followup_reminder_hour, hour), Modifier.padding(top = 12.dp))
            TextButton(onClick = { scope.launch { vm.settings.setReminderHour(hour + 1) } }, enabled = hour < 19) { Text(stringResource(R.string.followup_hour_later)) }
        }
        if (denied) {
            Text(stringResource(R.string.followup_permission_denied))
            TextButton(onClick = { context.startActivity(if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(R.string.followup_settings)) }
        }
    }
}
