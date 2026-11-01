package com.idontremember.personalcrm.ui.pilot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.idontremember.personalcrm.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.ui.components.SearchField
import com.idontremember.personalcrm.ui.components.SettingsButton
import com.idontremember.personalcrm.data.db.entity.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(onContact: (Long) -> Unit, onContacts: () -> Unit, onEvents: () -> Unit, onTags: () -> Unit, onSettings: () -> Unit, onAdd: () -> Unit, vm: PilotViewModel = hiltViewModel()) {
    val contacts by vm.contacts.collectAsState()
    val recent by vm.recent.collectAsState()
    var query by remember { mutableStateOf("") }
    Scaffold(modifier = Modifier.testTag("dashboard_screen"), topBar = { TopAppBar(title = { Text(stringResource(R.string.pilot_people)) }, actions = { SettingsButton(onClick = onSettings) }) }, floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pilot_quick_capture)) } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item { SearchField(query, { query = it }, stringResource(R.string.pilot_search), "clear_dashboard_search_button", Modifier.fillMaxWidth().testTag("dashboard_search")) }
            item { Row { TextButton(onClick = onContacts) { Text(stringResource(R.string.pilot_all_contacts)) }; TextButton(onClick = onEvents) { Text(stringResource(R.string.pilot_events)) }; TextButton(onClick = onTags) { Text(stringResource(R.string.pilot_tags)) } } }
            item { Text(if (query.isBlank()) stringResource(R.string.pilot_recent) else stringResource(R.string.pilot_search_results), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp)) }
            items(if (query.isBlank()) recent else contacts.filter { it.displayName.contains(query, true) }, key = { it.id }) { person ->
                ListItem(headlineContent = { Text(person.displayName) }, modifier = Modifier.testTag("dashboard_person_${person.id}").clickable { onContact(person.id) })
            }
        }
    }
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
    deleting?.let { tag -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.pilot_delete_tag, tag.name)) }, text = { Text(stringResource(R.string.pilot_delete_explanation)) }, confirmButton = { TextButton(onClick = { vm.deleteTag(tag); deleting = null }) { Text(stringResource(R.string.pilot_delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.pilot_cancel)) } }) }
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
    var label by remember { mutableStateOf("") }
    Column {
        Row { Text(stringResource(R.string.pilot_tags), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = { addingTag = true }) { Text(stringResource(R.string.pilot_add)) } }
        assigned.forEach { tag -> Row { Text(tag.name, modifier = Modifier.weight(1f)); TextButton(onClick = { vm.unassign(contactId, tag.id) }) { Text(stringResource(R.string.pilot_remove)) } } }
        Row { Text(stringResource(R.string.pilot_relationships), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = { addingLink = true }, modifier = Modifier.testTag("relationship_add")) { Text(stringResource(R.string.pilot_add)) } }
        links.forEach { link -> val other = if (link.contactA == contactId) link.contactB else link.contactA; Row { TextButton(onClick = { onContact(other) }, modifier = Modifier.weight(1f).testTag("relationship_person_$other")) { Text(stringResource(R.string.pilot_relation_display, link.label, contacts.find { it.id == other }?.displayName ?: stringResource(R.string.pilot_unknown))) }; TextButton(onClick = { vm.unlink(link) }, modifier = Modifier.testTag("relationship_remove_$other")) { Text(stringResource(R.string.pilot_remove)) } } }
    }
    if (addingTag) AlertDialog(onDismissRequest = { addingTag = false }, title = { Text(stringResource(R.string.pilot_add_tag)) }, text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { tags.filter { it !in assigned }.forEach { tag -> TextButton(onClick = { vm.assign(contactId, tag.id); addingTag = false }) { Text(tag.name) } } } }, confirmButton = { TextButton(onClick = { addingTag = false }) { Text(stringResource(R.string.pilot_done)) } })
    if (addingLink) AlertDialog(onDismissRequest = { addingLink = false }, title = { Text(stringResource(R.string.pilot_add_relationship)) }, text = { Column(Modifier.heightIn(max = 400.dp)) { OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.pilot_label)) }, modifier = Modifier.testTag("relationship_label")); LazyColumn { items(contacts.filter { it.id != contactId }, key = { it.id }) { person -> Row(Modifier.fillMaxWidth().selectable(selected = chosen == person.id, role = Role.RadioButton, onClick = { chosen = person.id })) { RadioButton(selected = chosen == person.id, onClick = null); Text(person.displayName) } } } } }, confirmButton = { TextButton(onClick = { chosen?.let { vm.link(contactId, it, label) }; chosen = null; label = ""; addingLink = false }, enabled = chosen != null && label.isNotBlank(), modifier = Modifier.testTag("relationship_confirm")) { Text(stringResource(R.string.pilot_add)) } }, dismissButton = { TextButton(onClick = { addingLink = false }) { Text(stringResource(R.string.pilot_cancel)) } })
}
