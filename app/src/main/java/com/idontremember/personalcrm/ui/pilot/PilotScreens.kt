package com.idontremember.personalcrm.ui.pilot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.ui.components.*

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
    vm: RecallViewModel = hiltViewModel(),
    onTag: (Long) -> Unit = {},
    onFollowUps: () -> Unit = {},
    onContactSearch: ((Long, String) -> Unit)? = null,
    hasDraft: Boolean = false
) {
    val query by vm.query.collectAsState()
    val kind by vm.kind.collectAsState()
    val dateDisplay by vm.dateDisplay.collectAsState()
    val results by vm.results.collectAsState()
    val failed by vm.error.collectAsState()
    val excerpts by vm.personExcerpts.collectAsState()
    val listState = rememberLazyListState()
    val recent = query.isBlank() && kind == null
    val rows = results.orEmpty()
    Scaffold(
        modifier = Modifier.testTag("dashboard_screen"),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.recall_home)) }, actions = { SettingsButton(onClick = onSettings) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(if (hasDraft) R.string.search_resume_draft else R.string.recall_add_note)) },
                modifier = Modifier.testTag("add_event_fab"))
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { SearchField(query, vm::search, stringResource(R.string.recall_search), "clear_dashboard_search_button", Modifier.fillMaxWidth().testTag("dashboard_search")) }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = onEvents, modifier = Modifier.testTag("nav_tab_events")) { Text(stringResource(R.string.search_history_title)) }
                    TextButton(onClick = onTags, modifier = Modifier.testTag("tags_shortcut")) { Text(stringResource(R.string.pilot_tags)) }
                    TextButton(onClick = onFollowUps, modifier = Modifier.testTag("followups_shortcut")) { Text(stringResource(R.string.search_followups_type)) }
                }
            }
            item { RecallTypeFilters(kind, vm::filter, includeEntities = true, prefix = "recall_filter") }
            when {
                failed -> item { ErrorState(stringResource(R.string.recall_load_failed), onRetry = vm::retry, testTag = "dashboard_error") }
                results == null -> item { LoadingState(testTag = "dashboard_loading") }
                rows.isEmpty() -> item {
                    EmptyState(message = stringResource(if (recent) R.string.recall_start else R.string.recall_no_results), testTag = "dashboard_empty")
                    if (recent) Text(stringResource(R.string.recall_start_detail), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
                }
                else -> {
                    val people = rows.filter { it.kind == "person" }
                    val tags = rows.filter { it.kind == "tag" }
                    val notes = rows.filter { it.kind != "person" && it.kind != "tag" }.let { if (recent) it.take(3) else it }
                    if (people.isNotEmpty()) {
                        item { SectionLabel(stringResource(R.string.recall_people)) }
                        items(people, key = { it.key }) { row ->
                            ListItem(headlineContent = { Text(row.title) }, supportingContent = { excerpts[row.id]?.let { Text(it) } }, modifier = Modifier.testTag("dashboard_person_${row.id}").clickable {
                                onContactSearch?.invoke(row.id, query) ?: onContact(row.id)
                            })
                        }
                    }
                    if (tags.isNotEmpty()) item { SectionLabel(stringResource(R.string.pilot_tags)) }
                    items(tags, key = { it.key }) { row ->
                        ListItem(headlineContent = { Text(row.title) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.search_open_tag, row.title)) },
                            modifier = Modifier.testTag("dashboard_tag_${row.id}").clickable { onTag(row.id) })
                    }
                    if (notes.isNotEmpty()) item { SectionLabel(stringResource(if (recent) R.string.recall_recent else R.string.search_saved_context)) }
                    items(notes, key = { it.key }) { row ->
                        RecallNoteRow(row, query, dateDisplay = dateDisplay, onClick = {
                            when (row.kind) { "fact" -> onFact(row.id); "followup" -> onFollowUp(row.id); else -> onEvent(row.id) }
                        })
                    }
                    if (recent && notes.isNotEmpty()) item { TextButton(onClick = onEvents, modifier = Modifier.testTag("recent_view_all")) { Text(stringResource(R.string.search_view_all)) } }
                }
            }
        }
    }
}

@Composable
fun RecallTypeFilters(selected: String?, onSelect: (String?) -> Unit, includeEntities: Boolean, prefix: String) {
    val kinds = if (includeEntities) listOf(null, "person", "event", "fact", "followup", "tag") else listOf(null, "event", "fact", "followup")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.forEach { kind ->
            val label = stringResource(when (kind) {
                "person" -> R.string.recall_people
                "event" -> R.string.search_notes_type
                "fact" -> R.string.search_facts_type
                "followup" -> R.string.search_followups_type
                "tag" -> R.string.pilot_tags
                else -> R.string.search_all_types
            })
            FilterChip(selected = selected == kind, onClick = { onSelect(kind) }, label = { Text(label) }, modifier = Modifier.testTag("${prefix}_${kind ?: "all"}"))
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(onBack: () -> Unit, onContact: (Long) -> Unit, vm: PilotViewModel = hiltViewModel(), onTag: ((Long) -> Unit)? = null) {
    val tags by vm.tags.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    val selected = selectedId
    if (selected != null) {
        TagDetailScreen(selected, onBack = { selectedId = null }, onContact = onContact, onEvent = {})
        return
    }
    Scaffold(modifier = Modifier.testTag("tags_screen"), topBar = {
        TopAppBar(title = { Text(stringResource(R.string.pilot_tags)) }, navigationIcon = { BackIcon(onBack) })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item { SearchField(query, { query = it }, stringResource(R.string.search_tag_hint), "clear_tags_search", Modifier.fillMaxWidth().testTag("tags_search")) }
            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.pilot_new_tag)) }, modifier = Modifier.weight(1f), singleLine = true)
                    IconButton(onClick = { vm.createTag(name); name = "" }, enabled = name.isNotBlank()) { Icon(Icons.Default.Add, stringResource(R.string.pilot_add_tag)) }
                }
            }
            items(tags.filter { it.name.contains(query, true) }, key = { it.id }) { tag ->
                ListItem(headlineContent = { Text(tag.name) }, leadingContent = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                    trailingContent = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            IconButton(onClick = { deleting = tag }) { Icon(Icons.Default.Delete, stringResource(R.string.search_delete_tag, tag.name)) }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.search_open_tag, tag.name))
                        }
                    }, modifier = Modifier.testTag("tag_${tag.id}").clickable { onTag?.invoke(tag.id) ?: run { selectedId = tag.id } })
            }
        }
    }
    deleting?.let { tag ->
        AlertDialog(modifier = Modifier.exposeTestTags(), onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.pilot_delete_tag, tag.name)) }, text = { Text(stringResource(R.string.pilot_delete_explanation)) },
            confirmButton = { TextButton(onClick = { vm.deleteTag(tag); deleting = null }) { Text(stringResource(R.string.pilot_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.pilot_cancel)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailScreen(tagId: Long, onBack: () -> Unit, onContact: (Long) -> Unit, onEvent: (Long) -> Unit, vm: PilotViewModel = hiltViewModel(), recall: RecallViewModel = hiltViewModel()) {
    val tags by vm.tags.collectAsState()
    val tag = tags.find { it.id == tagId }
    val people by remember(tagId) { vm.dao.peopleForTag(tagId) }.collectAsState(initial = null)
    val noteIds by remember(tagId) { vm.dao.noteIdsForTag(tagId) }.collectAsState(initial = null)
    val entries by recall.entries.collectAsState()
    val dateDisplay by recall.dateDisplay.collectAsState()
    val notes = entries?.filter { it.kind == "event" && it.id in noteIds.orEmpty() }?.sortedByDescending { it.date }
    Scaffold(modifier = Modifier.testTag("tag_detail_screen"), topBar = {
        TopAppBar(title = { Text(tag?.name ?: stringResource(R.string.pilot_tags)) }, navigationIcon = { BackIcon(onBack) })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (tag == null && entries != null) item { Text(stringResource(R.string.search_tag_missing)) }
            item { SectionLabel(stringResource(R.string.recall_people)) }
            when {
                people == null -> item { LoadingState() }
                people.orEmpty().isEmpty() -> item { Text(stringResource(R.string.search_tag_people_empty)) }
                else -> items(people.orEmpty(), key = { "person:${it.id}" }) { person ->
                    ListItem(headlineContent = { Text(person.displayName) }, supportingContent = {
                        Text(stringResource(when { person.direct && person.viaNote -> R.string.search_tag_both; person.direct -> R.string.search_tag_direct; else -> R.string.search_tag_via_note }))
                    }, modifier = Modifier.testTag("tag_person_${person.id}").clickable { onContact(person.id) })
                }
            }
            item { SectionLabel(stringResource(R.string.search_notes_type)) }
            when {
                notes == null || noteIds == null -> item { LoadingState() }
                notes.isEmpty() -> item { Text(stringResource(R.string.search_tag_notes_empty)) }
                else -> items(notes, key = { it.key }) { row ->
                    Box(Modifier.testTag("tag_note_${row.id}")) { RecallNoteRow(row, "", dateDisplay, onClick = { onEvent(row.id) }) }
                }
            }
        }
    }
}

@Composable
internal fun BackIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) }
}
