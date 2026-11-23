package com.idontremember.personalcrm.ui.contacts

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.ui.components.exposeTestTags
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.search.RecallSearch
import com.idontremember.personalcrm.ui.addevent.AddEventSheet
import com.idontremember.personalcrm.ui.components.*
import com.idontremember.personalcrm.ui.followups.FollowUpsSection
import com.idontremember.personalcrm.ui.followups.FollowUpEditor
import com.idontremember.personalcrm.ui.followups.FollowUpReader
import com.idontremember.personalcrm.ui.reading.FactReaderSheet
import com.idontremember.personalcrm.ui.reading.RecordPreview
import com.idontremember.personalcrm.ui.recall.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: Long,
    onBack: () -> Unit,
    onEventClick: (Long) -> Unit,
    onContactClick: (Long) -> Unit = {},
    initialQuery: String = "",
    viewModel: ContactDetailViewModel = hiltViewModel(),
    recall: RecallViewModel = hiltViewModel()
) {
    LaunchedEffect(contactId) { viewModel.setContactId(contactId) }
    val context = LocalContext.current
    val contact by viewModel.contact.collectAsState()
    val loaded by viewModel.contactLoaded.collectAsState()
    val interactions by viewModel.interactionSnapshot.collectAsStateWithLifecycle()
    val facts by viewModel.facts.collectAsState()
    val events by viewModel.events.collectAsState()
    val dateDisplay by viewModel.dateDisplay.collectAsState()
    val entries by recall.entries.collectAsState()
    var query by rememberSaveable(contactId) { mutableStateOf(initialQuery) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var management by rememberSaveable { mutableStateOf(false) }
    var allFacts by rememberSaveable { mutableStateOf(false) }
    var allEvents by rememberSaveable { mutableStateOf(false) }
    var addNote by rememberSaveable { mutableStateOf(false) }
    var addFact by rememberSaveable { mutableStateOf(false) }
    var readingFact by rememberSaveable { mutableStateOf<Long?>(null) }
    var readingFollowUp by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingFact by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingFollowUp by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingAndroid by rememberSaveable { mutableStateOf<Long?>(null) }
    val linking by viewModel.linking.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val deleted by viewModel.deletedFact.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.recall_fact_deleted)
    val undoLabel = stringResource(R.string.recall_undo)
    val actionFailedMessage = stringResource(R.string.recall_action_failed)
    val failedMessage = stringResource(R.string.recall_link_failed)
    val unavailable = stringResource(R.string.pilot_contact_unavailable)
    LaunchedEffect(deleted) { deleted?.let { id ->
        if (snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) viewModel.restoreFact(id)
        viewModel.consumeDeletedFact()
    } }
    LaunchedEffect(operationError) { if (operationError) { snackbar.showSnackbar(actionFailedMessage); viewModel.clearOperationError() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            pendingAndroid = runCatching { context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null } }.getOrNull()
            if (pendingAndroid == null) Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) picker.launch(null) }
    val listState = rememberLazyListState()
    Scaffold(
        topBar = { TopAppBar(title = { Text(contact?.displayName ?: stringResource(R.string.title_contact)) }, navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) }
        }, actions = {
            if (contact != null) IconButton(onClick = { newName = contact?.displayName.orEmpty(); rename = true }, modifier = Modifier.testTag("edit_person_button")) {
                Icon(Icons.Default.Edit, stringResource(if (contact?.androidContactId == null) R.string.people_edit_name else R.string.reading_edit_person))
            }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (contact != null) Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { addNote = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("person_add_note")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recall_add_note))
                }
            }
        }
    ) { padding ->
        when {
            !loaded -> LoadingState(Modifier.fillMaxSize().padding(padding), "contact_detail_loading_state")
            contact == null -> ErrorState(stringResource(R.string.error_contact_not_found), modifier = Modifier.fillMaxSize().padding(padding), testTag = "contact_detail_error_state")
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).testTag("contact_detail_screen"), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    if (contact?.androidContactId == null) Text(stringResource(R.string.people_app_only_status), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("app_only_status"))
                    interactions?.let { snapshot ->
                        val latest = snapshot.forPerson(contactId).lastInteractionAt
                        Text(stringResource(R.string.people_last_interaction, latest?.let { formatEventDateText(it, dateDisplay, now = snapshot.asOf) }
                            ?: stringResource(R.string.people_no_interactions)), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp).testTag("last_interaction"))
                    }
                }
                item { SearchField(query, { query = it }, stringResource(R.string.recall_person_search), "clear_person_search", Modifier.fillMaxWidth().testTag("person_search")) }
                if (query.isNotBlank()) {
                    val matches = RecallSearch.find(entries.orEmpty(), query, contactId).filter { it.kind.isCapture }
                    if (matches.isEmpty()) item { EmptyState(stringResource(R.string.recall_no_results), testTag = "person_search_empty") }
                    items(matches, key = { it.key }) { row -> RecallNoteRow(row, query, dateDisplay = dateDisplay) { row.target.openCapture(onEventClick, { readingFact = it }, { readingFollowUp = it }) } }
                } else {
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel(stringResource(R.string.section_facts))
                        IconButton(onClick = { addFact = true }, modifier = Modifier.size(48.dp).testTag("fact_add_button")) { Icon(Icons.Default.Add, stringResource(R.string.cd_add_fact)) }
                    } }
                    if (facts.isEmpty()) item { EmptyState(stringResource(R.string.empty_facts), testTag = "facts_empty_state") }
                    items(if (allFacts) facts else facts.take(3), key = { "fact_${it.id}" }) { fact ->
                        ListItem(
                            headlineContent = { RecordPreview(fact.text, onOpen = { readingFact = fact.id }) },
                            supportingContent = { Text(stringResource(R.string.pilot_recorded, formatEventDateText(fact.createdAt, dateDisplay))) },
                            modifier = Modifier.fillMaxWidth().testTag("fact_row_${fact.id}").clickable { readingFact = fact.id }
                        )
                    }
                    if (facts.size > 3) item { TextButton(onClick = { allFacts = !allFacts }) { Text(stringResource(if(allFacts) R.string.recall_less_facts else R.string.recall_more_facts)) } }
                    item { SectionLabel(stringResource(if(allEvents) R.string.recall_all_history else R.string.recall_recent_notes)) }
                    if (events.isEmpty()) item { EmptyState(stringResource(R.string.empty_contact_events), testTag = "contact_events_empty_state") }
                    items(if(allEvents) events else events.take(3), key = { "event_${it.id}" }) { event ->
                        Card(onClick = { onEventClick(event.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("event_preview_${event.id}")) {
                            Column(Modifier.padding(16.dp)) { RecordPreview(event.note, onOpen = { onEventClick(event.id) }); Text(formatEventDateText(event.eventDate, dateDisplay), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    if (events.size > 3) item { TextButton(onClick = { allEvents = !allEvents }) { Text(stringResource(if(allEvents) R.string.action_cancel else R.string.recall_all_history)) } }
                    item { FollowUpsSection(contactId, onContact = onContactClick) }
                    item { TextButton(onClick = { management = !management }, modifier = Modifier.testTag("person_management")) { Text(stringResource(R.string.recall_person_details)) } }
                    if (management) {
                        item { ProfileLinks(contactId, onContactClick) }
                    }
                }
            }
        }
    }
    if (addNote || addFact || editingFact != null) AddEventSheet(
        onDismiss = { addNote = false; addFact = false; editingFact = null },
        onSaved = { addNote = false; addFact = false; editingFact = null },
        initialContactId = contactId, existingFactId = editingFact, initialIsFact = addFact
    )
    readingFact?.let { id -> FactReaderSheet(factId = id, onDismiss = { readingFact = null }, onContact = { readingFact = null; onContactClick(it) }, onEdit = { readingFact = null; editingFact = it }) }
    readingFollowUp?.let { id -> FollowUpReader(existingId = id, onDismiss = { readingFollowUp = null }, onContact = { readingFollowUp = null; onContactClick(it) }, onEdit = { readingFollowUp = null; editingFollowUp = it }) }
    editingFollowUp?.let { id -> FollowUpEditor(existingId = id, onDismiss = { editingFollowUp = null }) }
    if(rename) AlertDialog(modifier = Modifier.exposeTestTags(),
        onDismissRequest = { rename = false }, title = { Text(stringResource(if (contact?.androidContactId == null) R.string.people_edit_name else R.string.reading_edit_person)) },
        text = { Column {
            OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.recall_person_name)) }, singleLine = true,
                enabled = contact?.androidContactId == null, modifier = Modifier.fillMaxWidth().testTag("rename_person_name"))
            contact?.androidContactId?.let { androidId ->
                Text(stringResource(R.string.reading_edit_android_name), style = MaterialTheme.typography.bodySmall)
                TextButton(modifier = Modifier.testTag("open_android_contact"), onClick = {
                    val current = contact
                    val uri = current?.lookupKey?.let { ContactsContract.Contacts.getLookupUri(androidId, it) } ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, androidId)
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show() }
                }) { Text(stringResource(R.string.pilot_open_contact)) }
            }
            if (contact?.androidContactId == null) TextButton(onClick = {
                if(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) picker.launch(null)
                else permission.launch(Manifest.permission.READ_CONTACTS)
            }, enabled = !linking, modifier = Modifier.testTag("link_android_contact")) { Text(stringResource(R.string.recall_link_android)) }
        } },
        confirmButton = { TextButton(onClick = { if (contact?.androidContactId == null) viewModel.rename(newName) { rename = false } else rename = false }, enabled = newName.isNotBlank(), modifier = Modifier.testTag("save_person_name")) { Text(stringResource(if (contact?.androidContactId == null) R.string.action_save else R.string.reading_close)) } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(R.string.action_cancel)) } }
    )
    pendingAndroid?.let { id -> AlertDialog(modifier = Modifier.exposeTestTags(),
        onDismissRequest = { pendingAndroid = null }, title = { Text(stringResource(R.string.recall_confirm_link)) },
        text = { Text(stringResource(R.string.recall_confirm_link_detail)) },
        confirmButton = { TextButton(onClick = { pendingAndroid = null; viewModel.linkAndroidContact(id) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { pendingAndroid = null }) { Text(stringResource(R.string.action_cancel)) } }
    ) }
}
