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
import androidx.compose.material.icons.filled.Close
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
        Row { Text(stringResource(R.string.pilot_tags), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); IconButton(onClick = { addingTag = true }) { Icon(Icons.Default.Add, stringResource(R.string.pilot_add_tag)) } }
        assigned.forEach { tag -> Row { Text(tag.name, modifier = Modifier.weight(1f)); IconButton(onClick = { vm.unassign(contactId, tag.id) }) { Icon(Icons.Default.Close, stringResource(R.string.pilot_remove)) } } }
        Row { Text(stringResource(R.string.pilot_relationships), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); IconButton(onClick = { addingLink = true }, modifier = Modifier.testTag("relationship_add")) { Icon(Icons.Default.Add, stringResource(R.string.pilot_add_relationship)) } }
        links.forEach { link -> val other = if (link.contactA == contactId) link.contactB else link.contactA; Row { TextButton(onClick = { onContact(other) }, modifier = Modifier.weight(1f).testTag("relationship_person_$other")) { Text(stringResource(R.string.pilot_relation_display, link.label, contacts.find { it.id == other }?.displayName ?: stringResource(R.string.pilot_unknown))) }; IconButton(onClick = { vm.unlink(link) }, modifier = Modifier.testTag("relationship_remove_$other")) { Icon(Icons.Default.Close, stringResource(R.string.pilot_remove)) } } }
    }
    if (addingTag) AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { addingTag = false }, title = { Text(stringResource(R.string.pilot_add_tag)) }, text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { SearchField(tagQuery, { tagQuery = it }, stringResource(R.string.recall_tag_search), "clear_tag_picker_search", Modifier.fillMaxWidth()); tags.filter { it !in assigned && it.name.contains(tagQuery, true) }.forEach { tag -> TextButton(onClick = { vm.assign(contactId, tag.id); addingTag = false }) { Text(tag.name) } } } }, confirmButton = { TextButton(onClick = { addingTag = false }) { Text(stringResource(R.string.pilot_done)) } })
    if (addingLink) AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { addingLink = false }, title = { Text(stringResource(R.string.pilot_add_relationship)) }, text = { Column(Modifier.heightIn(max = 400.dp)) { OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.pilot_label)) }, modifier = Modifier.testTag("relationship_label")); SearchField(personQuery, { personQuery = it }, stringResource(R.string.recall_management_search), "clear_relationship_search", Modifier.fillMaxWidth()); LazyColumn { items(contacts.filter { it.id != contactId && it.displayName.contains(personQuery, true) }, key = { it.id }) { person -> Row(Modifier.fillMaxWidth().selectable(selected = chosen == person.id, role = Role.RadioButton, onClick = { chosen = person.id })) { RadioButton(selected = chosen == person.id, onClick = null); Text(person.displayName) } } } } }, confirmButton = { TextButton(onClick = { chosen?.let { vm.link(contactId, it, label) }; chosen = null; label = ""; addingLink = false }, enabled = chosen != null && label.isNotBlank(), modifier = Modifier.testTag("relationship_confirm")) { Text(stringResource(R.string.pilot_add)) } }, dismissButton = { TextButton(onClick = { addingLink = false }) { Text(stringResource(R.string.pilot_cancel)) } })
}
