package com.idontremember.personalcrm.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.idontremember.personalcrm.ui.components.exposeTestTags
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.ui.components.PersonPicker
import com.idontremember.personalcrm.ui.components.SearchField
import com.idontremember.personalcrm.ui.components.SettingsButton
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.ui.components.EmptyState
import com.idontremember.personalcrm.ui.components.ErrorState
import com.idontremember.personalcrm.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsListScreen(
    onContactClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsListViewModel = hiltViewModel(),
    onContactSearch: ((Long, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.syncContacts() }
    val importContacts = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) viewModel.syncContacts()
        else permission.launch(Manifest.permission.READ_CONTACTS)
    }
    var addPerson by rememberSaveable { mutableStateOf(false) }
    val contacts by viewModel.contacts.collectAsState()
    val excerpts by viewModel.matchingExcerpts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // P2: sync-result copy resolved from strings.xml before the coroutine shows it.
    val syncFeedback: String? = when (val state = syncState) {
        is ContactsListViewModel.SyncState.Success ->
            stringResource(R.string.sync_result, state.result.added, state.result.updated)

        is ContactsListViewModel.SyncState.Error ->
            stringResource(
                R.string.sync_error,
                state.message ?: stringResource(R.string.error_sync_failed)
            )

        else -> null
    }

    // Show sync result as snackbar
    LaunchedEffect(syncState) {
        if (syncFeedback != null) {
            snackbarHostState.showSnackbar(syncFeedback)
            viewModel.clearSyncState()
        }
    }

    Scaffold(
        modifier = modifier.testTag("contacts_list_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recall_people)) },
                actions = {
                    IconButton(
                        onClick = importContacts,
                        enabled = syncState !is ContactsListViewModel.SyncState.Syncing,
                        modifier = Modifier
                            // a11y: 48dp touch target (M3 IconButton defaults to 40dp).
                            .size(48.dp)
                            .testTag("sync_button")
                    ) {
                        if (syncState is ContactsListViewModel.SyncState.Syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_sync))
                        }
                    }
                    SettingsButton(onClick = onSettingsClick)
                }
            )
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { addPerson = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.recall_add_person)) }, modifier = Modifier.testTag("add_person_button")) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            SearchField(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                hint = stringResource(R.string.recall_people_search),
                clearTag = "clear_contacts_search_button",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search_field")
            )

            Row(Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = importContacts, enabled = syncState !is ContactsListViewModel.SyncState.Syncing) { Text(stringResource(R.string.recall_import)) }
            }
            val contactList = contacts
            val error = loadError
            when {
                error != null -> ErrorState(
                    message = error,
                    onRetry = { viewModel.retryLoad() },
                    modifier = Modifier.fillMaxSize(),
                    testTag = "contacts_error_state",
                    retryTestTag = "contacts_error_retry_button"
                )

                contactList == null -> LoadingState(
                    modifier = Modifier.fillMaxSize(),
                    testTag = "contacts_loading_state"
                )

                contactList.isEmpty() -> EmptyState(
                    message = if (searchQuery.isBlank()) stringResource(R.string.empty_contacts)
                    else stringResource(R.string.empty_contacts_search),
                    modifier = Modifier.fillMaxSize(),
                    testTag = "contacts_empty_state"
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("contacts_list")
                ) {
                    items(contactList, key = { it.id }) { contact ->
                        ContactListItem(
                            contact = contact,
                            excerpt = excerpts[contact.id],
                            onClick = { onContactSearch?.invoke(contact.id, searchQuery) ?: onContactClick(contact.id) }
                        )
                    }
                }
            }
        }
    }
    if (addPerson) PersonPicker(
        onDismiss = { addPerson = false },
        onPersonSelected = { addPerson = false; onContactClick(it.id) },
        startCreating = true
    )

}

@Composable
private fun ContactListItem(
    contact: Contact,
    excerpt: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(contact.displayName) },
        supportingContent = {
            val details = listOfNotNull(contact.phoneNumber, contact.email).joinToString(" · ")
            val context = excerpt?.takeIf { it.isNotBlank() } ?: details
            if (context.isNotBlank()) Text(context)
        },
        leadingContent = {
            if (contact.photoUri != null) {
                AsyncImage(
                    model = contact.photoUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("contact_list_item")
    )
}
