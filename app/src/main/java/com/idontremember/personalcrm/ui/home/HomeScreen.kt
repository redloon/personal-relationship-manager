package com.idontremember.personalcrm.ui.home

import com.idontremember.personalcrm.data.search.RecallKind
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

import com.idontremember.personalcrm.ui.recall.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onContact: (Long) -> Unit,
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
                    val people = rows.filter { it.kind == RecallKind.PERSON }
                    val tags = rows.filter { it.kind == RecallKind.TAG }
                    val notes = rows.filter { it.kind != RecallKind.PERSON && it.kind != RecallKind.TAG }.let { if (recent) it.take(3) else it }
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
                            row.target.openCapture(onEvent, onFact, onFollowUp)
                        })
                    }
                    if (recent && notes.isNotEmpty()) item { TextButton(onClick = onEvents, modifier = Modifier.testTag("recent_view_all")) { Text(stringResource(R.string.search_view_all)) } }
                }
            }
        }
    }
}
