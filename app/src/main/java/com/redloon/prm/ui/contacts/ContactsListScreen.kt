package com.redloon.prm.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.redloon.prm.R
import com.redloon.prm.data.contacts.PeopleBulkAction
import com.redloon.prm.data.contacts.PeopleListItem
import com.redloon.prm.data.contacts.PeopleListScope
import com.redloon.prm.data.contacts.PeopleSort
import com.redloon.prm.data.contacts.ReconnectCandidate
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.settings.DateDisplay
import com.redloon.prm.ui.components.EmptyState
import com.redloon.prm.ui.components.ErrorState
import com.redloon.prm.ui.components.LoadingState
import com.redloon.prm.ui.components.PersonPicker
import com.redloon.prm.ui.components.SearchField
import com.redloon.prm.ui.components.SettingsButton
import com.redloon.prm.ui.components.exposeTestTags
import com.redloon.prm.ui.components.formatEventDateText
import com.redloon.prm.ui.components.relativeDate
import com.redloon.prm.ui.components.relativeDateStrings
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsListScreen(
    onContactClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsListViewModel = hiltViewModel(),
    scope: PeopleListScope = PeopleListScope.ALL,
    onBack: () -> Unit = {},
    onHiddenPeople: () -> Unit = {},
    onAppOnlyPeople: () -> Unit = {}
) {
    val hidden = scope == PeopleListScope.HIDDEN
    val secondary = scope != PeopleListScope.ALL
    val context = LocalContext.current
    val automaticSyncEnabled by viewModel.automaticContactSyncEnabled.collectAsStateWithLifecycle()
    val onboardingDismissed by
        viewModel.contactImportOnboardingDismissed.collectAsStateWithLifecycle()
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.importContacts() else viewModel.onContactPermissionDenied()
        }
    val runDirectorySync = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            if (automaticSyncEnabled) {
                viewModel.refreshContacts()
            } else {
                viewModel.importContacts()
            }
        } else {
            permission.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var showSort by rememberSaveable { mutableStateOf(false) }
    val openSort = {
        keyboard?.hide()
        focus.clearFocus()
        showSort = true
    }
    val order by viewModel.sort.collectAsStateWithLifecycle()
    val canDelete by viewModel.canDeleteSelection.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()
    val requestDelete = {
        keyboard?.hide()
        focus.clearFocus()
        viewModel.runAction(PeopleBulkAction.DELETE)
    }
    var addPerson by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var reviewingDuplicates by rememberSaveable { mutableStateOf(false) }
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val reconnectCandidates by viewModel.reconnectCandidates.collectAsStateWithLifecycle()
    val reconnectBusy by viewModel.reconnectBusy.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val busy by viewModel.bulkBusy.collectAsState()
    val bulkFailed by viewModel.bulkFailed.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(R.string.people_undo)
    val resources = LocalResources.current
    val contextTitle = when (scope) {
        PeopleListScope.ALL -> null
        PeopleListScope.HIDDEN -> stringResource(R.string.people_hidden_title)
        PeopleListScope.APP_ONLY -> stringResource(R.string.people_app_only_title)
    }
    val contextIcon = when (scope) {
        PeopleListScope.ALL -> null
        PeopleListScope.HIDDEN -> Icons.Default.VisibilityOff
        PeopleListScope.APP_ONLY -> Icons.Default.PersonOutline
    }
    val headerColors = if (hidden) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }

    LaunchedEffect(scope) { viewModel.configure(scope) }
    if (!secondary) {
        LifecycleResumeEffect(viewModel, context) {
            viewModel.onPrimaryPeopleResumed(
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED
            )
            onPauseOrDispose {}
        }
    }
    BackHandler(enabled = selection.active || busy) { viewModel.clearSelection() }
    LaunchedEffect(viewModel, resources, undo) {
        viewModel.visibilityChanges.collectLatest { change ->
            snackbar.currentSnackbarData?.dismiss()
            val message = resources.getString(
                if (change.hidden) R.string.people_hidden_count else R.string.people_unhidden_count,
                change.ids.size
            )
            if (snackbar.showSnackbar(message, undo, duration = SnackbarDuration.Long) ==
                SnackbarResult.ActionPerformed
            ) {
                viewModel.changeVisibility(change.ids, !change.hidden)
            }
        }
    }

    LaunchedEffect(viewModel, resources) {
        viewModel.feedback.collect { feedback ->
            val message = when (feedback) {
                is PeopleFeedback.Deleted -> resources.getQuantityString(
                    R.plurals.people_deleted_count,
                    feedback.count,
                    feedback.count
                )
                PeopleFeedback.DeleteFailed -> resources.getString(R.string.people_delete_failed)
                PeopleFeedback.Protected -> resources.getString(R.string.people_delete_protected)
                PeopleFeedback.PreviewChanged -> resources.getString(R.string.people_delete_changed)
                PeopleFeedback.SortFailed -> resources.getString(R.string.people_sort_failed)
                PeopleFeedback.Reconnected -> resources.getString(R.string.people_reconnected)
                PeopleFeedback.ReconnectFailed ->
                    resources.getString(R.string.people_reconnect_failed)
            }
            snackbar.showSnackbar(message)
        }
    }

    val syncFeedback: String? = when (val state = syncState) {
        is ContactsListViewModel.SyncState.Success -> if (state.announce) {
            when {
                state.result.reconnected > 0 && state.result.reviewable > 0 -> pluralStringResource(
                    R.plurals.sync_reconnected_and_reviewable,
                    state.result.reconnected,
                    state.result.reconnected,
                    state.result.reviewable
                )
                state.result.reconnected > 0 -> pluralStringResource(
                    R.plurals.sync_reconnected,
                    state.result.reconnected,
                    state.result.reconnected
                )
                state.result.reviewable > 0 -> pluralStringResource(
                    R.plurals.sync_reviewable,
                    state.result.reviewable,
                    state.result.reviewable
                )
                else -> pluralStringResource(
                    R.plurals.sync_result,
                    state.result.added,
                    state.result.added,
                    state.result.updated
                )
            }
        } else {
            null
        }
        ContactsListViewModel.SyncState.PermissionDenied ->
            stringResource(R.string.contacts_permission_required)
        is ContactsListViewModel.SyncState.Error -> stringResource(
            R.string.sync_error,
            state.message ?: stringResource(R.string.error_sync_failed)
        )
        else -> null
    }
    LaunchedEffect(syncState) {
        if (syncFeedback != null) {
            val reviewable = (syncState as? ContactsListViewModel.SyncState.Success)
                ?.result?.reviewable?.takeIf { it > 0 }
            val result = snackbar.showSnackbar(
                syncFeedback,
                actionLabel = reviewable?.let {
                    resources.getString(R.string.people_review_action)
                },
                duration = if (reviewable != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) reviewingDuplicates = true
        }
        if (syncState !is ContactsListViewModel.SyncState.Syncing) viewModel.clearSyncState()
    }
    LaunchedEffect(reviewingDuplicates, reconnectCandidates) {
        if (reviewingDuplicates && reconnectCandidates.isEmpty()) reviewingDuplicates = false
    }

    Scaffold(
        modifier = modifier.testTag(
            when (scope) {
                PeopleListScope.ALL -> "contacts_list_screen"
                PeopleListScope.HIDDEN -> "hidden_people_screen"
                PeopleListScope.APP_ONLY -> "app_only_people_screen"
            }
        ).exposeTestTags(),
        topBar = {
            if (selection.active) {
                PeopleSelectionToolbar(
                    selection = selection, busy = busy,
                    actionLabel = stringResource(
                        if (hidden) R.string.people_unhide else R.string.people_hide
                    ),
                    onClose = viewModel::clearSelection,
                    onAction = {
                        snackbar.currentSnackbarData?.dismiss()
                        viewModel.runAction(
                            if (hidden) PeopleBulkAction.UNHIDE else PeopleBulkAction.HIDE
                        )
                    },
                    onSelectAll = viewModel::selectAllResults,
                    onClear = viewModel::clearSelection,
                    onDelete = requestDelete, canDelete = canDelete, onSort = openSort,
                    contextTitle = contextTitle, contextIcon = contextIcon, colors = headerColors
                )
            } else {
                TopAppBar(
                    modifier = Modifier.testTag("people_appbar"),
                    colors = headerColors,
                    expandedHeight = if (secondary) contextAppBarHeight() else 64.dp,
                    title = {
                        if (contextTitle != null) {
                            PeopleContextTitle(contextTitle, contextIcon)
                        } else {
                            Text(stringResource(R.string.recall_people))
                        }
                    },
                    navigationIcon = {
                        if (secondary) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag(
                                    if (hidden) "hidden_people_back" else "app_only_people_back"
                                )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.cd_back)
                                )
                            }
                        }
                    },
                    actions = {
                        if (!secondary) {
                            SettingsButton(onClick = onSettingsClick)
                        }
                        Box {
                            IconButton(onClick = {
                                menu = true
                            }, modifier = Modifier.testTag("people_menu")) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    stringResource(R.string.people_actions)
                                )
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = {
                                menu = false
                            }, modifier = Modifier.exposeTestTags()) {
                                if (!secondary) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (automaticSyncEnabled) {
                                                        R.string.contacts_refresh_action
                                                    } else {
                                                        R.string.contacts_import_action
                                                    }
                                                )
                                            )
                                        },
                                        enabled = syncState !is
                                            ContactsListViewModel.SyncState.Syncing,
                                        onClick = {
                                            menu = false
                                            runDirectorySync()
                                        },
                                        modifier = Modifier.testTag("contacts_sync_menu")
                                    )
                                }
                                if (!secondary && reconnectCandidates.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.people_review_duplicates))
                                        },
                                        onClick = {
                                            menu = false
                                            reviewingDuplicates = true
                                        },
                                        modifier = Modifier.testTag("review_duplicates_menu")
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                R.string.people_sort_menu_label,
                                                stringResource(order.label())
                                            )
                                        )
                                    },
                                    onClick = {
                                        menu = false
                                        openSort()
                                    },
                                    modifier = Modifier.testTag("people_sort_menu")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.people_select))
                                    },
                                    onClick = {
                                        menu = false
                                        viewModel.startSelection()
                                    },
                                    modifier = Modifier.testTag("people_select_menu")
                                )
                                if (!secondary) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.people_app_only_title))
                                        },
                                        onClick = {
                                            menu = false
                                            onAppOnlyPeople()
                                        },
                                        modifier = Modifier.testTag("app_only_people_menu")
                                    )
                                }
                                if (!secondary) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.people_hidden_title))
                                        },
                                        onClick = {
                                            menu = false
                                            onHiddenPeople()
                                        },
                                        modifier = Modifier.testTag("hidden_people_menu")
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!secondary &&
                !selection.active
            ) {
                ExtendedFloatingActionButton(
                    onClick = { addPerson = true },
                    icon = {
                        Icon(Icons.Default.Add, null)
                    },
                    text = { Text(stringResource(R.string.recall_add_person)) },
                    modifier = Modifier.testTag("add_person_button")
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (secondary) {
                Text(
                    stringResource(
                        if (hidden) {
                            R.string.people_hidden_explanation
                        } else {
                            R.string.people_app_only_explanation
                        }
                    ),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!secondary && !onboardingDismissed) {
                ContactImportOnboardingCard(
                    onImport = runDirectorySync,
                    onNotNow = viewModel::dismissContactImportOnboarding
                )
            }
            SearchField(
                searchQuery,
                viewModel::onSearchQueryChanged,
                stringResource(
                    when (scope) {
                        PeopleListScope.ALL -> R.string.people_directory_search
                        PeopleListScope.HIDDEN -> R.string.people_hidden_search
                        PeopleListScope.APP_ONLY -> R.string.people_app_only_search
                    }
                ),
                "clear_contacts_search_button",
                Modifier.fillMaxWidth().padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ).testTag("search_field")
            )
            if (bulkFailed) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.people_visibility_failed),
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(
                        onClick = viewModel::retryVisibilityChange,
                        enabled = !busy,
                        modifier = Modifier.testTag("people_visibility_retry")
                    ) {
                        Text(stringResource(R.string.people_retry))
                    }
                }
            }
            val list = contacts
            val error = loadError
            when {
                error != null -> ErrorState(
                    message = error,
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.fillMaxSize(),
                    testTag = "contacts_error_state",
                    retryTestTag = "contacts_error_retry_button"
                )
                list == null -> LoadingState(
                    Modifier.fillMaxSize(),
                    testTag = "contacts_loading_state"
                )
                list.isEmpty() -> EmptyState(
                    message = stringResource(
                        when {
                            searchQuery.isNotBlank() -> R.string.empty_contacts_search
                            hidden -> R.string.people_hidden_empty
                            secondary -> R.string.people_app_only_empty
                            else -> R.string.empty_contacts
                        }
                    ),
                    modifier = Modifier.fillMaxSize(),
                    testTag = "contacts_empty_state"
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize().testTag("contacts_list"),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(list, key = { it.id }) { contact ->
                        SelectablePersonRow(
                            contact,
                            order,
                            selection.active,
                            contact.id in selection.ids,
                            !busy,
                            onClick = {
                                if (selection.active) {
                                    viewModel.togglePerson(contact.id)
                                } else {
                                    onContactClick(contact.id)
                                }
                            },
                            onLongClick = { viewModel.togglePerson(contact.id) }
                        )
                    }
                }
            }
        }
    }
    if (showSort) {
        PeopleSortDialog(order, onSelect = {
            viewModel.changeSort(it)
            showSort = false
        }, onDismiss = {
            showSort =
                false
        })
    }
    deletion?.let {
        PeopleDeletionDialog(it, busy, viewModel::confirmDeletion, viewModel::cancelDeletion)
    }
    if (addPerson) {
        PersonPicker(
            onDismiss = { addPerson = false },
            onPersonSelected = {
                addPerson = false
                onContactClick(it.id)
            },
            startCreating = true
        )
    }
    if (reviewingDuplicates) {
        reconnectCandidates.firstOrNull()?.let { candidate ->
            PossibleDuplicateDialog(
                candidate = candidate,
                remaining = reconnectCandidates.size,
                busy = reconnectBusy,
                onReconnect = { viewModel.reconnect(candidate) },
                onDifferent = { viewModel.dismissReconnect(candidate) },
                onDismiss = { if (!reconnectBusy) reviewingDuplicates = false }
            )
        }
    }
}

@Composable
private fun PossibleDuplicateDialog(
    candidate: ReconnectCandidate,
    remaining: Int,
    busy: Boolean,
    onReconnect: () -> Unit,
    onDifferent: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                pluralStringResource(
                    R.plurals.people_possible_duplicates_title,
                    remaining,
                    remaining
                )
            )
        },
        text = {
            Column {
                ContactComparison(
                    stringResource(R.string.people_saved_profile),
                    candidate.savedProfile
                )
                ContactComparison(
                    stringResource(R.string.people_android_contact),
                    candidate.androidProfile
                )
                Text(
                    stringResource(R.string.people_reconnect_explanation),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onReconnect,
                enabled = !busy,
                modifier = Modifier.testTag("reconnect_profiles")
            ) { Text(stringResource(R.string.people_reconnect)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDifferent,
                enabled = !busy,
                modifier = Modifier.testTag("dismiss_duplicate")
            ) { Text(stringResource(R.string.people_not_same)) }
        },
        modifier = Modifier.testTag("duplicate_review_dialog")
    )
}

@Composable
private fun ContactComparison(label: String, contact: Contact) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(contact.displayName, style = MaterialTheme.typography.bodyLarge)
        contact.phoneNumber?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        contact.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ContactImportOnboardingCard(onImport: () -> Unit, onNotNow: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("contact_import_onboarding")
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.contacts_import_onboarding_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.contacts_import_onboarding_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
            ) {
                TextButton(
                    onClick = onNotNow,
                    modifier = Modifier.testTag("contact_import_not_now")
                ) { Text(stringResource(R.string.contacts_import_not_now)) }
                TextButton(
                    onClick = onImport,
                    modifier = Modifier.testTag("contact_import_confirm")
                ) { Text(stringResource(R.string.contacts_import_action)) }
            }
        }
    }
}

/** Optional context stays visible without coupling selection to a particular bulk action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleSelectionToolbar(
    selection: PeopleSelection,
    busy: Boolean,
    actionLabel: String,
    onClose: () -> Unit,
    onAction: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    onSort: () -> Unit,
    contextTitle: String? = null,
    contextIcon: ImageVector? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors()
) {
    val count =
        pluralStringResource(
            R.plurals.people_selected_count,
            selection.ids.size,
            selection.ids.size
        )
    val countModifier = Modifier.testTag("people_selection_count").semantics {
        liveRegion =
            LiveRegionMode.Polite
    }
    val actionEnabled = selection.ids.isNotEmpty() && !busy
    Column(Modifier.testTag("people_appbar")) {
        TopAppBar(
            colors = colors,
            expandedHeight = if (contextTitle != null) contextAppBarHeight() else 64.dp,
            title = {
                if (contextTitle != null) {
                    PeopleContextTitle(contextTitle, contextIcon)
                } else {
                    Text(
                        count,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = countModifier
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onClose,
                    enabled = !busy,
                    modifier = Modifier.testTag("people_selection_close")
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.people_close_selection))
                }
            },
            actions = {
                if (contextTitle == null) {
                    TextButton(
                        onClick = onAction,
                        enabled = actionEnabled,
                        modifier = Modifier.testTag("people_visibility_action")
                    ) { Text(actionLabel) }
                }
                PeopleSelectionMenu(busy, onSelectAll, onClear, onDelete, canDelete, onSort)
            }
        )
        if (contextTitle !=
            null
        ) {
            Surface(color = colors.containerColor, contentColor = colors.titleContentColor) {
                // A separate row gives the context and actions enough width at large text sizes.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        count,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = countModifier.weight(1f)
                    )
                    TextButton(
                        onClick = onAction,
                        enabled = actionEnabled,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colors.actionIconContentColor
                        ),
                        modifier = Modifier.testTag("people_visibility_action")
                    ) { Text(actionLabel) }
                }
            }
        }
    }
}

@Composable
private fun PeopleSelectionMenu(
    busy: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    onSort: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = {
            menu = true
        }, enabled = !busy, modifier = Modifier.testTag("people_selection_menu")) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.people_selection_actions))
        }
        DropdownMenu(expanded = menu, onDismissRequest = {
            menu = false
        }, modifier = Modifier.exposeTestTags()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.people_select_all)) },
                onClick = {
                    menu = false
                    onSelectAll()
                },
                modifier = Modifier.testTag("people_select_all")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.people_clear_selection)) },
                onClick = {
                    menu = false
                    onClear()
                },
                modifier = Modifier.testTag("people_clear_selection")
            )
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.people_sort_title))
                },
                enabled = !busy,
                onClick = {
                    menu = false
                    onSort()
                },
                modifier = Modifier.testTag("people_sort_menu")
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.people_delete))
                        if (!canDelete) {
                            Text(
                                stringResource(R.string.people_delete_protected),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                enabled = canDelete && !busy,
                onClick = {
                    menu = false
                    onDelete()
                },
                modifier = Modifier.testTag("people_delete_action")
            )
        }
    }
}

@Composable
private fun PeopleContextTitle(title: String, icon: ImageVector?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp).testTag("people_context_icon")
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            modifier = Modifier.weight(1f).testTag("people_context_title").semantics { heading() }
        )
    }
}

/** Reserve two title lines using the current font scale, keeping the header pinned. */
@Composable
private fun contextAppBarHeight(): Dp = with(LocalDensity.current) {
    (MaterialTheme.typography.titleLarge.lineHeight.toDp() * 2 + 16.dp).coerceAtLeast(64.dp)
}

@Composable
private fun SelectablePersonRow(
    row: PeopleListItem,
    order: PeopleSort,
    selecting: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val contact = row.contact
    val selectLabel = stringResource(R.string.people_select_person)
    Box(Modifier.testTag("person_row_${contact.id}")) {
        ListItem(
            headlineContent = { Text(contact.displayName) },
            supportingContent = {
                Column {
                    val recent =
                        row.stats.lastInteractionAt?.let {
                            relativeDate(it, relativeDateStrings(LocalResources.current), row.asOf)
                        }
                            ?: stringResource(R.string.people_no_interactions)
                    Text(stringResource(R.string.people_last_interaction, recent))
                    if (order == PeopleSort.MOST_INTERACTIONS ||
                        order == PeopleSort.FEWEST_INTERACTIONS
                    ) {
                        Text(
                            androidx.compose.ui.res.pluralStringResource(
                                R.plurals.people_interactions,
                                row.stats.interactionCount,
                                row.stats.interactionCount
                            )
                        )
                    }
                    if (order == PeopleSort.ADDED_NEWEST || order == PeopleSort.ADDED_OLDEST) {
                        Text(
                            stringResource(
                                R.string.people_added_date,
                                formatEventDateText(
                                    contact.createdAt,
                                    DateDisplay.ABSOLUTE,
                                    R.string.date_pattern_short
                                )
                            )
                        )
                    }
                }
            },
            leadingContent = {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                } else if (contact.photoUri != null) {
                    AsyncImage(
                        contact.photoUri,
                        null,
                        Modifier.size(40.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        null,
                        Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor =
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            modifier = Modifier.combinedClickable(
                enabled = enabled,
                role = if (selecting) Role.Checkbox else Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = selectLabel
            )
                .semantics { if (selecting) this.selected = selected }
                .testTag("contact_list_item")
        )
    }
}
