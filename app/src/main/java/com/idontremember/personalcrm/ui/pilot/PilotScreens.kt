package com.idontremember.personalcrm.ui.pilot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.idontremember.personalcrm.ui.components.exposeTestTags
import com.idontremember.personalcrm.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.ui.components.*
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.search.RecallEntry
import com.idontremember.personalcrm.data.search.RecallSearch
import com.idontremember.personalcrm.ui.followups.FollowUpsSection
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(
    onContact: (Long) -> Unit,
    onContacts: () -> Unit,
    onEvents: () -> Unit,
    onTags: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onEvent: (Long) -> Unit = {},
    onFact: (Long) -> Unit = {},
    onFollowUp: (Long) -> Unit = {},
    vm: RecallViewModel = hiltViewModel()
) {
    val query by vm.query.collectAsState()
    val dateDisplay by vm.dateDisplay.collectAsState()
    val results by vm.results.collectAsState()
    val failed by vm.error.collectAsState()
    val listState = rememberLazyListState()
    val rows = results.orEmpty()
    Scaffold(
        modifier = Modifier.testTag("dashboard_screen"),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.recall_home)) }, actions = { SettingsButton(onClick = onSettings) }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.recall_add_note)) }, modifier = Modifier.testTag("add_event_fab")) }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { SearchField(query, vm::search, stringResource(R.string.recall_search), "clear_dashboard_search_button", Modifier.fillMaxWidth().testTag("dashboard_search")) }
            item { Row { TextButton(onClick = onEvents, modifier = Modifier.testTag("nav_tab_events")) { Text(stringResource(R.string.recall_history)) }; TextButton(onClick = onTags) { Text(stringResource(R.string.pilot_tags)) } } }
            when {
                failed -> item { ErrorState(stringResource(R.string.recall_load_failed), onRetry = vm::retry, testTag = "dashboard_error") }
                results == null -> item { LoadingState(testTag = "dashboard_loading") }
                rows.isEmpty() -> item {
                    EmptyState(message = stringResource(if (query.isBlank()) R.string.recall_start else R.string.recall_no_results), testTag = "dashboard_empty")
                    if (query.isBlank()) Text(stringResource(R.string.recall_start_detail), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
                }
                else -> {
                    val people = rows.filter { it.kind == "person" }
                    val notes = rows.filter { it.kind != "person" }.let { if (query.isBlank()) it.take(20) else it }
                    if (people.isNotEmpty()) {
                        item { SectionLabel(stringResource(R.string.recall_people)) }
                        items(people, key = { it.key }) { row ->
                            ListItem(headlineContent = { Text(row.title) }, modifier = Modifier.testTag("dashboard_person_${row.id}").clickable { onContact(row.id) })
                        }
                    }
                    if (notes.isNotEmpty()) item { SectionLabel(stringResource(if (query.isBlank()) R.string.recall_recent else R.string.recall_notes)) }
                    items(notes, key = { it.key }) { row ->
                        RecallNoteRow(row, query, dateDisplay = dateDisplay, onClick = { when (row.kind) { "fact" -> onFact(row.id); "followup" -> onFollowUp(row.id); else -> onEvent(row.id) } })
                    }
                }
            }
            if (query.isBlank()) item { FollowUpsSection() }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun RecallNoteRow(row: RecallEntry, query: String, dateDisplay: DateDisplay = DateDisplay.ABSOLUTE, onClick: () -> Unit) {
    val kind = stringResource(when (row.kind) { "fact" -> R.string.recall_fact; "followup" -> R.string.recall_followup; else -> R.string.recall_event })
    ListItem(
        headlineContent = { Text(RecallSearch.excerpt(row.text, query), maxLines = 3, overflow = TextOverflow.Ellipsis) },
        overlineContent = { Text(listOf(kind, row.title).filter { it.isNotBlank() }.joinToString(" · ")) },
        supportingContent = { val date = formatEventDateText(row.date, dateDisplay); Text(if(row.kind == "fact") stringResource(R.string.pilot_recorded, date) else date) },
        modifier = Modifier.fillMaxWidth().testTag("recall_${row.kind}_${row.id}").clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(onBack: () -> Unit, onContact: (Long) -> Unit, vm: PilotViewModel = hiltViewModel()) {
    val tags by vm.tags.collectAsState()
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Tag?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(selected?.name ?: stringResource(R.string.pilot_tags)) }, navigationIcon = { TextButton(onClick = { if (selected != null) selected = null else onBack() }) { Text(stringResource(R.string.pilot_back)) } }) }) { padding ->
        val tag = selected
        if (tag == null) LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            item { Row { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.pilot_new_tag)) }, modifier = Modifier.weight(1f), singleLine = true); TextButton(onClick = { vm.createTag(name); name = "" }, enabled = name.isNotBlank()) { Text(stringResource(R.string.pilot_add)) } } }
            items(tags, key = { it.id }) { t -> ListItem(headlineContent = { Text(t.name) }, modifier = Modifier.clickable { selected = t }, trailingContent = { TextButton(onClick = { deleting = t }) { Text(stringResource(R.string.pilot_delete)) } }) }
        } else {
            val people by remember(tag.id) { vm.dao.contactsForTag(tag.id) }.collectAsState(initial = emptyList())
            LazyColumn(Modifier.padding(padding)) { items(people, key = { it.id }) { person -> ListItem(headlineContent = { Text(person.displayName) }, modifier = Modifier.clickable { onContact(person.id) }) } }
        }
    }
    deleting?.let { tag -> AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.pilot_delete_tag, tag.name)) }, text = { Text(stringResource(R.string.pilot_delete_explanation)) }, confirmButton = { TextButton(onClick = { vm.deleteTag(tag); deleting = null }) { Text(stringResource(R.string.pilot_delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.pilot_cancel)) } }) }
}

@Composable
fun ProfileLinks(contactId: Long, onContact: (Long) -> Unit, vm: PilotViewModel = hiltViewModel()) {
    val tags by vm.tags.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val assigned by remember(contactId) { vm.dao.tagsForContact(contactId) }.collectAsState(initial = emptyList())
    val links by remember(contactId) { vm.dao.relationshipsFor(contactId) }.collectAsState(initial = emptyList())
    var addingTag by remember { mutableStateOf(false) }
    var addingLink by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Long?>(null) }
    var label by rememberSaveable { mutableStateOf("") }
    var personQuery by rememberSaveable { mutableStateOf("") }
    var tagQuery by rememberSaveable { mutableStateOf("") }
    Column {
        Row { Text(stringResource(R.string.pilot_tags), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = { addingTag = true }) { Text(stringResource(R.string.pilot_add)) } }
        assigned.forEach { tag -> Row { Text(tag.name, modifier = Modifier.weight(1f)); TextButton(onClick = { vm.unassign(contactId, tag.id) }) { Text(stringResource(R.string.pilot_remove)) } } }
        Row { Text(stringResource(R.string.pilot_relationships), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = { addingLink = true }, modifier = Modifier.testTag("relationship_add")) { Text(stringResource(R.string.pilot_add)) } }
        links.forEach { link -> val other = if (link.contactA == contactId) link.contactB else link.contactA; Row { TextButton(onClick = { onContact(other) }, modifier = Modifier.weight(1f).testTag("relationship_person_$other")) { Text(stringResource(R.string.pilot_relation_display, link.label, contacts.find { it.id == other }?.displayName ?: stringResource(R.string.pilot_unknown))) }; TextButton(onClick = { vm.unlink(link) }, modifier = Modifier.testTag("relationship_remove_$other")) { Text(stringResource(R.string.pilot_remove)) } } }
    }
    if (addingTag) AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { addingTag = false }, title = { Text(stringResource(R.string.pilot_add_tag)) }, text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { SearchField(tagQuery, { tagQuery = it }, stringResource(R.string.recall_tag_search), "clear_tag_picker_search", Modifier.fillMaxWidth()); tags.filter { it !in assigned && it.name.contains(tagQuery, true) }.forEach { tag -> TextButton(onClick = { vm.assign(contactId, tag.id); addingTag = false }) { Text(tag.name) } } } }, confirmButton = { TextButton(onClick = { addingTag = false }) { Text(stringResource(R.string.pilot_done)) } })
    if (addingLink) AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { addingLink = false }, title = { Text(stringResource(R.string.pilot_add_relationship)) }, text = { Column(Modifier.heightIn(max = 400.dp)) { OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.pilot_label)) }, modifier = Modifier.testTag("relationship_label")); SearchField(personQuery, { personQuery = it }, stringResource(R.string.recall_management_search), "clear_relationship_search", Modifier.fillMaxWidth()); LazyColumn { items(contacts.filter { it.id != contactId && it.displayName.contains(personQuery, true) }, key = { it.id }) { person -> Row(Modifier.fillMaxWidth().selectable(selected = chosen == person.id, role = Role.RadioButton, onClick = { chosen = person.id })) { RadioButton(selected = chosen == person.id, onClick = null); Text(person.displayName) } } } } }, confirmButton = { TextButton(onClick = { chosen?.let { vm.link(contactId, it, label) }; chosen = null; label = ""; addingLink = false }, enabled = chosen != null && label.isNotBlank(), modifier = Modifier.testTag("relationship_confirm")) { Text(stringResource(R.string.pilot_add)) } }, dismissButton = { TextButton(onClick = { addingLink = false }) { Text(stringResource(R.string.pilot_cancel)) } })
}
