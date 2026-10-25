package com.idontremember.personalcrm.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.ui.components.EmptyState
import com.idontremember.personalcrm.ui.components.ErrorState
import com.idontremember.personalcrm.ui.components.LoadingState
import com.idontremember.personalcrm.ui.components.formatEventDateText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: Long,
    onBack: () -> Unit,
    onEventClick: (Long) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(contactId) {
        viewModel.setContactId(contactId)
    }

    val contact by viewModel.contact.collectAsState()
    val contactLoaded by viewModel.contactLoaded.collectAsState()
    val facts by viewModel.facts.collectAsState()
    val events by viewModel.events.collectAsState()
    val dateDisplay by viewModel.dateDisplay.collectAsState()
    val showAddFactDialog by viewModel.showAddFactDialog.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact?.displayName ?: stringResource(R.string.title_contact)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        val currentContact = contact
        if (!contactLoaded) {
            LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                testTag = "contact_detail_loading_state"
            )
        } else if (currentContact == null) {
            ErrorState(
                message = stringResource(R.string.error_contact_not_found),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                testTag = "contact_detail_error_state"
            )
        } else LazyColumn(
            modifier = Modifier
                .testTag("contact_detail_screen")
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Contact header
            item {
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                // a11y: name + phone + email read as one ordered utterance.
                                .semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentContact.photoUri != null) {
                                AsyncImage(
                                    model = currentContact.photoUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = currentContact.displayName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                currentContact.phoneNumber?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                currentContact.email?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
            }

            // Facts section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.section_facts),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { viewModel.onAddFactClick() },
                        modifier = Modifier
                            // a11y: 48dp touch target (M3 IconButton defaults to 40dp).
                            .size(48.dp)
                            .testTag("fact_add_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_fact))
                    }
                }
            }

            if (facts.isEmpty()) {
                item {
                    EmptyState(
                        message = stringResource(R.string.empty_facts),
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "facts_empty_state"
                    )
                }
            }

            items(facts, key = { "fact_${it.id}" }) { fact ->
                FactItem(
                    fact = fact,
                    onDelete = { viewModel.deleteFact(fact) }
                )
            }

            // Events section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.section_events),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (events.isEmpty()) {
                item {
                    EmptyState(
                        message = stringResource(R.string.empty_contact_events),
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "contact_events_empty_state"
                    )
                }
            }

            items(events, key = { "event_${it.id}" }) { event ->
                EventItem(
                    event = event,
                    dateDisplay = dateDisplay,
                    onClick = { onEventClick(event.id) }
                )
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Add fact dialog
    if (showAddFactDialog) {
        AddFactDialog(
            onDismiss = { viewModel.onDismissAddFact() },
            onConfirm = { text -> viewModel.addFact(text) }
        )
    }
}

@Composable
private fun FactItem(
    fact: ContactFact,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = fact.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete_fact),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EventItem(
    event: Event,
    dateDisplay: DateDisplay,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = event.note,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatEventDateText(event.eventDate, dateDisplay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddFactDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_fact_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.fact_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fact_text_field")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}