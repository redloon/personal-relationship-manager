package com.idontremember.personalcrm.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
fun EventDetailScreen(
    eventId: Long,
    onBack: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(eventId) {
        viewModel.setEventId(eventId)
    }

    val event by viewModel.event.collectAsState()
    val eventLoaded by viewModel.eventLoaded.collectAsState()
    val dateDisplay by viewModel.dateDisplay.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val editNote by viewModel.editNote.collectAsState()
    val saved by viewModel.saved.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_event)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (!isEditing) {
                        IconButton(
                            onClick = { viewModel.startEditing() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit_event))
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_delete_event),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val currentEvent = event
        when {
            !eventLoaded -> LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                testTag = "event_detail_loading_state"
            )

            currentEvent == null -> ErrorState(
                message = stringResource(R.string.error_event_not_found),
                onRetry = { viewModel.retryLoad() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                testTag = "event_detail_error_state",
                retryTestTag = "event_detail_error_retry_button"
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("event_detail_screen")
            ) {
                if (isEditing) {
                    // Edit mode
                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { viewModel.onNoteChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 10,
                        label = { Text(stringResource(R.string.field_note)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { viewModel.cancelEditing() },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            // For simplicity, save with current contacts/tags (not editable in this simple edit view)
                            viewModel.saveEdit(
                                editNote,
                                contacts.map { it.id },
                                tags.map { it.name }
                            )
                        }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                } else {
                    // View mode
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = currentEvent.note,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    // Date
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = formatEventDateText(
                            currentEvent.eventDate,
                            dateDisplay,
                            absolutePatternRes = R.string.date_pattern_event_full
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    // Empty state for linked people/tags (P2 shared EmptyState)
                    if (contacts.isEmpty() && tags.isEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        EmptyState(
                            message = stringResource(R.string.empty_event_links),
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "event_links_empty_state"
                        )
                    }

                    // Contacts
                    if (contacts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.section_people),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            contacts.forEach { contact ->
                                ContactChip(name = contact.displayName)
                            }
                        }
                    }

                    // Tags
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.section_tags),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tags.forEach { tag ->
                                TagChip(name = tag.name)
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_event_title)) },
            text = { Text(stringResource(R.string.dialog_delete_event_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteEvent()
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}