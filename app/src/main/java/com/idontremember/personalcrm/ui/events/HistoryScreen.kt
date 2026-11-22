package com.idontremember.personalcrm.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.ui.components.*
import com.idontremember.personalcrm.ui.pilot.BackIcon
import com.idontremember.personalcrm.ui.pilot.RecallNoteRow
import com.idontremember.personalcrm.ui.pilot.RecallTypeFilters
import com.idontremember.personalcrm.ui.pilot.RecallViewModel

/** Complete capture history. Person/tag directories remain search scopes on Home. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onEvent: (Long) -> Unit,
    onFact: (Long) -> Unit,
    onFollowUp: (Long) -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onContact: (Long) -> Unit = {},
    vm: RecallViewModel = hiltViewModel()
) {
    val rows by vm.history.collectAsState()
    val entries by vm.entries.collectAsState()
    val query by vm.query.collectAsState()
    val kind by vm.kind.collectAsState()
    val failed by vm.error.collectAsState()
    val dates by vm.dateDisplay.collectAsState()
    val people = entries.orEmpty().filter { it.kind == "person" }.associate { it.id to it.title }
    Scaffold(
        modifier = Modifier.testTag("events_list_screen"),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.search_history_title)) }, navigationIcon = { BackIcon(onBack) }, actions = { SettingsButton(onSettings) }) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd, modifier = Modifier.testTag("add_event_fab")) { Icon(Icons.Default.Add, stringResource(R.string.cd_add_event)) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, vm::search, stringResource(R.string.search_history_hint), "clear_events_search_button", Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("events_search_field"))
            Box(Modifier.padding(horizontal = 16.dp)) { RecallTypeFilters(kind, vm::filter, includeEntities = false, prefix = "history_filter") }
            when {
                failed -> ErrorState(stringResource(R.string.recall_load_failed), modifier = Modifier.fillMaxSize(), onRetry = vm::retry, testTag = "events_error_state")
                rows == null -> LoadingState(Modifier.fillMaxSize(), testTag = "events_loading_state")
                rows.orEmpty().isEmpty() -> EmptyState(stringResource(if (query.isBlank()) R.string.search_history_empty else R.string.search_history_no_matches), Modifier.fillMaxSize(), testTag = "events_empty_state")
                else -> LazyColumn(Modifier.fillMaxSize().testTag("events_list"), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(rows.orEmpty(), key = { it.key }) { row ->
                        Column {
                            RecallNoteRow(row, query, dates, onClick = { when (row.kind) { "fact" -> onFact(row.id); "followup" -> onFollowUp(row.id); else -> onEvent(row.id) } })
                            val linkedPeople = row.personIds.trim(',').split(',').mapNotNull(String::toLongOrNull)
                            if (linkedPeople.isNotEmpty()) FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                linkedPeople.forEach { id ->
                                    people[id]?.let { name -> ContactChip(name, Modifier.testTag("event_contact_chip_$id"), onClick = { onContact(id) }) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
