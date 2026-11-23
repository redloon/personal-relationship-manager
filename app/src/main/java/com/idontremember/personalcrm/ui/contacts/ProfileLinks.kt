package com.idontremember.personalcrm.ui.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.ui.components.SearchField
import com.idontremember.personalcrm.ui.components.TagPicker
import com.idontremember.personalcrm.ui.components.exposeTestTags

@Composable
fun ProfileLinks(
    contactId: Long,
    onContact: (Long) -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileLinksViewModel = hiltViewModel()
) {
    val tags by vm.tags.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val linkedPeople by vm.linkedPeople.collectAsState()
    val assigned by remember(contactId) {
        vm.assignedTags(contactId)
    }.collectAsState(initial = emptyList())
    val links by remember(contactId) {
        vm.relationships(contactId)
    }.collectAsState(initial = emptyList())
    var addingTag by remember { mutableStateOf(false) }
    var addingLink by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Long?>(null) }
    var label by rememberSaveable { mutableStateOf("") }
    var personQuery by rememberSaveable { mutableStateOf("") }
    var tagQuery by rememberSaveable { mutableStateOf("") }
    Column(modifier) {
        ProfileSectionHeader(
            stringResource(
                R.string.pilot_tags
            ),
            stringResource(R.string.pilot_add_tag),
            {
                addingTag =
                    true
            }
        )
        assigned.forEach { tag ->
            Row {
                Text(tag.name, Modifier.weight(1f))
                IconButton(onClick = {
                    vm.unassign(contactId, tag.id)
                }) { Icon(Icons.Default.Close, stringResource(R.string.pilot_remove)) }
            }
        }
        ProfileSectionHeader(
            stringResource(R.string.pilot_relationships),
            stringResource(R.string.pilot_add_relationship),
            { addingLink = true },
            Modifier.testTag("relationship_add")
        )
        links.forEach { link ->
            val other = if (link.contactA == contactId) link.contactB else link.contactA
            Row {
                TextButton(onClick = {
                    onContact(other)
                }, Modifier.weight(1f).testTag("relationship_person_$other")) {
                    Text(
                        stringResource(
                            R.string.pilot_relation_display,
                            link.label,
                            linkedPeople.find { it.id == other }?.displayName
                                ?: stringResource(R.string.pilot_unknown)
                        )
                    )
                }
                IconButton(onClick = {
                    vm.unlink(link)
                }, Modifier.testTag("relationship_remove_$other")) {
                    Icon(Icons.Default.Close, stringResource(R.string.pilot_remove))
                }
            }
        }
    }
    if (addingTag) {
        TagPicker(
            tags.filter { it !in assigned },
            tagQuery,
            { tagQuery = it },
            onDismiss = { addingTag = false },
            onSelect = {
                vm.assign(contactId, it.id)
                addingTag =
                    false
            }
        )
    }
    if (addingLink) {
        RelationshipDialog(
            contacts.filter { it.id != contactId }, label, { label = it }, personQuery, {
                personQuery =
                    it
            }, chosen, { chosen = it },
            onDismiss = { addingLink = false },
            onConfirm = {
                chosen?.let { vm.link(contactId, it, label) }
                chosen = null
                label = ""
                addingLink =
                    false
            }
        )
    }
}

@Composable
private fun ProfileSectionHeader(
    title: String,
    addLabel: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onAdd, modifier = modifier) { Icon(Icons.Default.Add, addLabel) }
    }
}

@Composable
private fun RelationshipDialog(
    people: List<Contact>,
    label: String,
    onLabel: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    chosen: Long?,
    onChoose: (Long) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.exposeTestTags(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pilot_add_relationship)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp)) {
                OutlinedTextField(
                    label,
                    onLabel,
                    label = {
                        Text(stringResource(R.string.pilot_label))
                    },
                    modifier = Modifier.testTag("relationship_label")
                )
                SearchField(
                    query,
                    onQuery,
                    stringResource(R.string.recall_management_search),
                    "clear_relationship_search",
                    Modifier.fillMaxWidth()
                )
                LazyColumn {
                    items(
                        people.filter {
                            it.displayName.contains(query, true)
                        },
                        key = { it.id }
                    ) { person ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                chosen == person.id,
                                role = Role.RadioButton,
                                onClick = {
                                    onChoose(person.id)
                                }
                            )
                        ) {
                            RadioButton(selected = chosen == person.id, onClick = null)
                            Text(person.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = chosen != null && label.isNotBlank(),
                modifier = Modifier.testTag("relationship_confirm")
            ) {
                Text(stringResource(R.string.pilot_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pilot_cancel)) }
        }
    )
}
