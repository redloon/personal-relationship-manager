package com.redloon.prm.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.redloon.prm.R
import com.redloon.prm.data.search.RecallKind
import com.redloon.prm.ui.components.ContactChip
import com.redloon.prm.ui.components.EmptyState
import com.redloon.prm.ui.components.ErrorState
import com.redloon.prm.ui.components.LoadingState
import com.redloon.prm.ui.components.SearchField
import com.redloon.prm.ui.components.SettingsButton
import com.redloon.prm.ui.recall.BackIcon
import com.redloon.prm.ui.recall.RecallNoteRow
import com.redloon.prm.ui.recall.RecallTypeFilters
import com.redloon.prm.ui.recall.RecallViewModel
import com.redloon.prm.ui.recall.openCapture

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
    modifier: Modifier = Modifier,
    onContact: (Long) -> Unit = {},
    vm: RecallViewModel = hiltViewModel()
) {
    val rows by vm.history.collectAsState()
    val entries by vm.entries.collectAsState()
    val query by vm.query.collectAsState()
    val kind by vm.kind.collectAsState()
    val failed by vm.error.collectAsState()
    val dates by vm.dateDisplay.collectAsState()
    val people = entries.orEmpty().filter { it.kind == RecallKind.PERSON }.associate {
        it.id to
            it.title
    }
    Scaffold(
        modifier = modifier.testTag("events_list_screen"),
        topBar = {
            TopAppBar(title = {
                Text(stringResource(R.string.search_history_title))
            }, navigationIcon = { BackIcon(onBack) }, actions = { SettingsButton(onSettings) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.testTag("add_event_fab")) {
                Icon(Icons.Default.Add, stringResource(R.string.cd_add_event))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query,
                vm::search,
                stringResource(R.string.search_history_hint),
                "clear_events_search_button",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("events_search_field")
            )
            Box(Modifier.padding(horizontal = 16.dp)) {
                RecallTypeFilters(
                    kind,
                    vm::filter,
                    includeEntities = false,
                    prefix = "history_filter"
                )
            }
            when {
                failed -> ErrorState(
                    stringResource(R.string.recall_load_failed),
                    modifier = Modifier.fillMaxSize(),
                    onRetry = vm::retry,
                    testTag = "events_error_state"
                )
                rows == null -> LoadingState(
                    Modifier.fillMaxSize(),
                    testTag = "events_loading_state"
                )
                rows.orEmpty().isEmpty() -> EmptyState(
                    stringResource(
                        if (query.isBlank()) {
                            R.string.search_history_empty
                        } else {
                            R.string.search_history_no_matches
                        }
                    ),
                    Modifier.fillMaxSize(),
                    testTag = "events_empty_state"
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize().testTag("events_list"),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(rows.orEmpty(), key = { it.key }) { row ->
                        Column {
                            RecallNoteRow(row, query, dateDisplay = dates, onClick = {
                                row.target.openCapture(onEvent, onFact, onFollowUp)
                            })
                            val linkedPeople = row.personIds
                            if (linkedPeople.isNotEmpty()) {
                                FlowRow(
                                    Modifier.padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    linkedPeople.forEach { id ->
                                        people[id]?.let { name ->
                                            ContactChip(
                                                name,
                                                Modifier.testTag(
                                                    "event_contact_chip_$id"
                                                ),
                                                onClick = {
                                                    onContact(id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
