package com.idontremember.personalcrm.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.ui.components.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsListScreen(
    onContactClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsListViewModel = hiltViewModel(),
    onContactSearch: ((Long, String) -> Unit)? = null,
    hidden: Boolean = false,
    onBack: () -> Unit = {},
    onHiddenPeople: () -> Unit = {}
) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.syncContacts() }
    val importContacts = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) viewModel.syncContacts()
        else permission.launch(Manifest.permission.READ_CONTACTS)
    }
    var addPerson by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val contacts by viewModel.contacts.collectAsState()
    val excerpts by viewModel.matchingExcerpts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val busy by viewModel.bulkBusy.collectAsState()
    val bulkFailed by viewModel.bulkFailed.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(R.string.people_undo)
    val resources = LocalResources.current
    val contextTitle = if (hidden) stringResource(R.string.people_hidden_title) else null
    val contextIcon = if (hidden) Icons.Default.VisibilityOff else null
    val headerColors = if (hidden) TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        scrolledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
        titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        navigationIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        actionIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) else TopAppBarDefaults.topAppBarColors()

    LaunchedEffect(hidden) { viewModel.configure(hidden) }
    BackHandler(enabled = selection.active || busy) { viewModel.clearSelection() }
    LaunchedEffect(viewModel, resources, undo) {
        viewModel.visibilityChanges.collectLatest { change ->
            snackbar.currentSnackbarData?.dismiss()
            val message = resources.getString(
                if (change.hidden) R.string.people_hidden_count else R.string.people_unhidden_count,
                change.ids.size
            )
            if (snackbar.showSnackbar(message, undo, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed)
                viewModel.changeVisibility(change.ids, !change.hidden)
        }
    }

    val syncFeedback: String? = when (val state = syncState) {
        is ContactsListViewModel.SyncState.Success -> stringResource(R.string.sync_result, state.result.added, state.result.updated)
        is ContactsListViewModel.SyncState.Error -> stringResource(R.string.sync_error, state.message ?: stringResource(R.string.error_sync_failed))
        else -> null
    }
    LaunchedEffect(syncState) {
        if (syncFeedback != null) {
            snackbar.showSnackbar(syncFeedback)
            viewModel.clearSyncState()
        }
    }

    Scaffold(
        modifier = modifier.testTag(if (hidden) "hidden_people_screen" else "contacts_list_screen").exposeTestTags(),
        topBar = {
            if (selection.active) {
                PeopleSelectionToolbar(
                    selection = selection, busy = busy,
                    actionLabel = stringResource(if (hidden) R.string.people_unhide else R.string.people_hide),
                    onClose = viewModel::clearSelection,
                    onAction = { snackbar.currentSnackbarData?.dismiss(); viewModel.changeSelectedVisibility() },
                    onSelectAll = viewModel::selectAllResults,
                    onClear = viewModel::clearSelection,
                    contextTitle = contextTitle, contextIcon = contextIcon, colors = headerColors
                )
            } else TopAppBar(
                modifier = Modifier.testTag("people_appbar"),
                colors = headerColors,
                expandedHeight = if (hidden) contextAppBarHeight() else 64.dp,
                title = {
                    if (contextTitle != null) PeopleContextTitle(contextTitle, contextIcon)
                    else Text(stringResource(R.string.recall_people))
                },
                navigationIcon = {
                    if (hidden) IconButton(onClick = onBack, modifier = Modifier.testTag("hidden_people_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (!hidden) {
                        IconButton(onClick = importContacts, enabled = syncState !is ContactsListViewModel.SyncState.Syncing,
                            modifier = Modifier.size(48.dp).testTag("sync_button")) {
                            if (syncState is ContactsListViewModel.SyncState.Syncing) CircularProgressIndicator(Modifier.size(24.dp))
                            else Icon(Icons.Default.Refresh, stringResource(R.string.cd_sync))
                        }
                        SettingsButton(onClick = onSettingsClick)
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("people_menu")) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.people_actions))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.exposeTestTags()) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.people_select)) },
                                onClick = { menu = false; viewModel.startSelection() },
                                modifier = Modifier.testTag("people_select_menu"))
                            if (!hidden) DropdownMenuItem(text = { Text(stringResource(R.string.people_hidden_title)) },
                                onClick = { menu = false; onHiddenPeople() }, modifier = Modifier.testTag("hidden_people_menu"))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!hidden && !selection.active) ExtendedFloatingActionButton(onClick = { addPerson = true },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.recall_add_person)) },
                modifier = Modifier.testTag("add_person_button"))
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (hidden) Text(stringResource(R.string.people_hidden_explanation),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            SearchField(searchQuery, viewModel::onSearchQueryChanged,
                stringResource(if (hidden) R.string.people_hidden_search else R.string.recall_people_search),
                "clear_contacts_search_button",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search_field"))
            if (bulkFailed) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.people_visibility_failed), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::retryVisibilityChange, enabled = !busy, modifier = Modifier.testTag("people_visibility_retry")) {
                    Text(stringResource(R.string.people_retry))
                }
            }
            val list = contacts
            val error = loadError
            when {
                error != null -> ErrorState(message = error, onRetry = viewModel::retryLoad, modifier = Modifier.fillMaxSize(),
                    testTag = "contacts_error_state", retryTestTag = "contacts_error_retry_button")
                list == null -> LoadingState(Modifier.fillMaxSize(), testTag = "contacts_loading_state")
                list.isEmpty() -> EmptyState(
                    message = stringResource(when {
                        searchQuery.isNotBlank() -> R.string.empty_contacts_search
                        hidden -> R.string.people_hidden_empty
                        else -> R.string.empty_contacts
                    }), modifier = Modifier.fillMaxSize(), testTag = "contacts_empty_state")
                else -> LazyColumn(Modifier.fillMaxSize().testTag("contacts_list"), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(list, key = { it.id }) { contact ->
                        SelectablePersonRow(contact, if (hidden) null else excerpts[contact.id],
                            selection.active, contact.id in selection.ids, !busy,
                            onClick = {
                                if (selection.active) viewModel.togglePerson(contact.id)
                                else onContactSearch?.invoke(contact.id, searchQuery) ?: onContactClick(contact.id)
                            },
                            onLongClick = { viewModel.togglePerson(contact.id) })
                    }
                }
            }
        }
    }
    if (addPerson) PersonPicker(onDismiss = { addPerson = false },
        onPersonSelected = { addPerson = false; onContactClick(it.id) }, startCreating = true)
}

/** Optional context stays visible without coupling selection to a particular bulk action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleSelectionToolbar(
    selection: PeopleSelection, busy: Boolean, actionLabel: String,
    onClose: () -> Unit, onAction: () -> Unit, onSelectAll: () -> Unit, onClear: () -> Unit,
    contextTitle: String? = null, contextIcon: ImageVector? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors()
) {
    val count = stringResource(R.string.people_selected_count, selection.ids.size)
    val countModifier = Modifier.testTag("people_selection_count").semantics { liveRegion = LiveRegionMode.Polite }
    val actionEnabled = selection.ids.isNotEmpty() && !busy
    Column(Modifier.testTag("people_appbar")) {
        TopAppBar(
            colors = colors,
            expandedHeight = if (contextTitle != null) contextAppBarHeight() else 64.dp,
            title = {
                if (contextTitle != null) PeopleContextTitle(contextTitle, contextIcon)
                else Text(count, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = countModifier)
            },
            navigationIcon = {
                IconButton(onClick = onClose, enabled = !busy, modifier = Modifier.testTag("people_selection_close")) {
                    Icon(Icons.Default.Close, stringResource(R.string.people_close_selection))
                }
            },
            actions = {
                if (contextTitle == null) TextButton(onClick = onAction, enabled = actionEnabled,
                    modifier = Modifier.testTag("people_visibility_action")) { Text(actionLabel) }
                PeopleSelectionMenu(busy, onSelectAll, onClear)
            }
        )
        if (contextTitle != null) Surface(color = colors.containerColor, contentColor = colors.titleContentColor) {
            // A separate row gives the context and actions enough width at large text sizes.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(count, style = MaterialTheme.typography.titleMedium, modifier = countModifier.weight(1f))
                TextButton(onClick = onAction, enabled = actionEnabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.actionIconContentColor),
                    modifier = Modifier.testTag("people_visibility_action")) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun PeopleSelectionMenu(busy: Boolean, onSelectAll: () -> Unit, onClear: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.testTag("people_selection_menu")) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.people_selection_actions))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.exposeTestTags()) {
            DropdownMenuItem(text = { Text(stringResource(R.string.people_select_all)) },
                onClick = { menu = false; onSelectAll() }, modifier = Modifier.testTag("people_select_all"))
            DropdownMenuItem(text = { Text(stringResource(R.string.people_clear_selection)) },
                onClick = { menu = false; onClear() }, modifier = Modifier.testTag("people_clear_selection"))
        }
    }
}

@Composable
private fun PeopleContextTitle(title: String, icon: ImageVector?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp).testTag("people_context_icon"))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2,
            modifier = Modifier.weight(1f).testTag("people_context_title").semantics { heading() })
    }
}

/** Reserve two title lines using the current font scale, keeping the header pinned. */
@Composable
private fun contextAppBarHeight(): Dp = with(LocalDensity.current) {
    (MaterialTheme.typography.titleLarge.lineHeight.toDp() * 2 + 16.dp).coerceAtLeast(64.dp)
}

@Composable
private fun SelectablePersonRow(
    contact: Contact, excerpt: String?, selecting: Boolean, selected: Boolean, enabled: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    val selectLabel = stringResource(R.string.people_select_person)
    Box(Modifier.testTag("person_row_${contact.id}")) {
        ListItem(
            headlineContent = { Text(contact.displayName) },
            supportingContent = {
                val details = excerpt?.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(contact.phoneNumber, contact.email).joinToString(" · ")
                if (details.isNotBlank()) Text(details)
            },
            leadingContent = {
                if (selecting) Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                else if (contact.photoUri != null) AsyncImage(contact.photoUri, null,
                    Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            colors = ListItemDefaults.colors(containerColor =
                if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
            modifier = Modifier.combinedClickable(enabled = enabled,
                role = if (selecting) Role.Checkbox else Role.Button,
                onClick = onClick, onLongClick = onLongClick, onLongClickLabel = selectLabel)
                .semantics { if (selecting) this.selected = selected }
                .testTag("contact_list_item")
        )
    }
}
