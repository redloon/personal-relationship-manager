package com.idontremember.personalcrm.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.ui.components.ContactChip
import com.idontremember.personalcrm.ui.components.EmptyState
import com.idontremember.personalcrm.ui.components.ErrorState
import com.idontremember.personalcrm.ui.components.LoadingState
import com.idontremember.personalcrm.ui.components.TagChip
import com.idontremember.personalcrm.ui.components.formatEventDateText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventsListScreen(
    onEventClick: (Long) -> Unit,
    onAddEventClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventsListViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val dateDisplay by viewModel.dateDisplay.collectAsState()
    val loadError by viewModel.loadError.collectAsState()

    Scaffold(
        modifier = modifier.testTag("events_list_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_events)) },
                actions = {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            // a11y: 48dp touch target (M3 IconButton defaults to 40dp).
                            .size(48.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddEventClick,
                modifier = Modifier.testTag("add_event_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_event))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("events_search_field"),
                placeholder = { Text(stringResource(R.string.search_events_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            val eventList = events
            val error = loadError
            when {
                error != null -> ErrorState(
                    message = error,
                    onRetry = { viewModel.retryLoad() },
                    modifier = Modifier.fillMaxSize(),
                    testTag = "events_error_state",
                    retryTestTag = "events_error_retry_button"
                )

                eventList == null -> LoadingState(
                    modifier = Modifier.fillMaxSize(),
                    testTag = "events_loading_state"
                )

                eventList.isEmpty() -> EmptyState(
                    message = if (searchQuery.isBlank()) stringResource(R.string.empty_events)
                    else stringResource(R.string.empty_events_search),
                    modifier = Modifier.fillMaxSize(),
                    testTag = "events_empty_state"
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("events_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(eventList, key = { it.event.id }) { displayItem ->
                        EventListItem(
                            displayItem = displayItem,
                            dateDisplay = dateDisplay,
                            onClick = { onEventClick(displayItem.event.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventListItem(
    displayItem: EventDisplayItem,
    dateDisplay: DateDisplay,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Event note
            Text(
                text = displayItem.event.note,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Contact and tag chips
            if (displayItem.contacts.isNotEmpty() || displayItem.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    displayItem.contacts.forEach { contact ->
                        ContactChip(name = contact.displayName)
                    }
                    displayItem.tags.forEach { tag ->
                        TagChip(name = tag.name)
                    }
                }
            }

            // Date
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatEventDateText(displayItem.event.eventDate, dateDisplay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}