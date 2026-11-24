package com.redloon.prm.ui.followups

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redloon.prm.R
import com.redloon.prm.data.followups.FollowUpWithPerson
import com.redloon.prm.data.followups.FollowUpWorker
import com.redloon.prm.data.recovery.TrashKind
import com.redloon.prm.ui.components.PersonPicker
import com.redloon.prm.ui.components.SearchField
import com.redloon.prm.ui.components.exposeTestTags
import com.redloon.prm.ui.reading.RecordPreview
import com.redloon.prm.ui.settings.SettingsSwitchRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun FollowUpsSection(
    modifier: Modifier = Modifier,
    contactId: Long? = null,
    onContact: (Long) -> Unit = {},
    vm: FollowUpViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val related = items.filter { contactId == null || it.item.contactId == contactId }
    val pending = related.filter { it.item.completedAt == null }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var reading by rememberSaveable { mutableStateOf<Long?>(null) }
    if (contactId != null ||
        related.isNotEmpty()
    ) {
        Column(modifier.fillMaxWidth().testTag("followups_section")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.followup_title),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = {
                    adding = true
                }) { Text(stringResource(R.string.followup_add)) }
            }
            pending.take(3).forEach { FollowUpRow(it, onClick = { reading = it.item.id }) }
            if (pending.isEmpty()) {
                Text(
                    stringResource(R.string.followup_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (related.isNotEmpty()) {
                TextButton(onClick = {
                    showAll = true
                }) { Text(stringResource(R.string.followup_all)) }
            }
        }
    }
    if (showAll) {
        FollowUpsOverlay(contactId = contactId, onDismiss = {
            showAll = false
        }, onContact = onContact)
    }
    reading?.let { id ->
        FollowUpReader(existingId = id, onDismiss = { reading = null }, onContact = {
            reading =
                null
            onContact(it)
        }, onEdit = {
            reading = null
            editing = it
        })
    }
    if (adding ||
        editing != null
    ) {
        FollowUpEditor(contactId = contactId, existingId = editing, onDismiss = {
            adding =
                false
            editing = null
        })
    }
}

@Composable
private fun FollowUpRow(entry: FollowUpWithPerson, onClick: () -> Unit) {
    ListItem(
        headlineContent = { RecordPreview(entry.item.text, onClick) },
        overlineContent = { Text(entry.name) },
        leadingContent = {
            Icon(Icons.Default.EventAvailable, null, tint = MaterialTheme.colorScheme.tertiary)
        },
        supportingContent = { FollowUpStatus(entry.item.dueDate, entry.item.completedAt) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .35f)
        ),
        modifier = Modifier.fillMaxWidth().clickable(
            onClick = onClick
        ).testTag("followup_${entry.item.id}")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpsOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contactId: Long? = null,
    onContact: (Long) -> Unit = {},
    vm: FollowUpViewModel = hiltViewModel()
) {
    val entries by vm.items.collectAsStateWithLifecycle()
    var completed by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var reading by rememberSaveable { mutableStateOf<Long?>(null) }
    val visible = entries.filter {
        (contactId == null || it.item.contactId == contactId) &&
            (it.item.completedAt != null) == completed
    }
    ModalBottomSheet(
        modifier = modifier.exposeTestTags(),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxHeight(.85f).padding(horizontal = 16.dp).testTag("followups_list")) {
            Text(
                stringResource(R.string.followup_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Row {
                FilterChip(selected = !completed, onClick = {
                    completed = false
                }, label = { Text(stringResource(R.string.followup_pending)) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = completed, onClick = {
                    completed = true
                }, label = { Text(stringResource(R.string.followup_completed)) })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    adding = true
                }) { Text(stringResource(R.string.followup_add)) }
            }
            if (visible.isEmpty()) {
                Text(
                    stringResource(R.string.followup_empty),
                    Modifier.padding(vertical = 24.dp)
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(visible, key = { it.item.id }) { entry ->
                    FollowUpRow(entry) {
                        reading =
                            entry.item.id
                    }
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.followup_close)) }
        }
    }
    reading?.let { id ->
        FollowUpReader(existingId = id, onDismiss = { reading = null }, onContact = {
            reading =
                null
            onContact(it)
        }, onEdit = {
            reading = null
            editing = it
        })
    }
    if (adding ||
        editing != null
    ) {
        FollowUpEditor(contactId = contactId, existingId = editing, onDismiss = {
            adding =
                false
            editing = null
        })
    }
}

@Composable
fun ReminderPreferences(modifier: Modifier = Modifier, vm: FollowUpViewModel = hiltViewModel()) {
    val enabled by vm.reminders.collectAsStateWithLifecycle()
    val hour by vm.hour.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            denied = !granted
            scope.launch {
                vm.settings.setFollowUpRemindersEnabled(granted)
                if (granted) FollowUpWorker.ensureScheduled(context)
            }
        }
    Column(modifier.fillMaxWidth().testTag("reminder_settings")) {
        SettingsSwitchRow(
            title = stringResource(R.string.followup_reminders),
            checked = enabled,
            onCheckedChange = { value ->
                if (value &&
                    Build.VERSION.SDK_INT >= 33
                ) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch {
                        vm.settings.setFollowUpRemindersEnabled(value)
                        if (value) FollowUpWorker.ensureScheduled(context)
                    }
                }
            },
            testTag = "followup_reminders_toggle"
        )
        Text(
            stringResource(R.string.followup_reminder_explanation),
            style = MaterialTheme.typography.bodySmall
        )
        if (enabled) {
            Row {
                TextButton(
                    onClick = {
                        scope.launch { vm.settings.setReminderHour(hour - 1) }
                    },
                    enabled =
                    hour > 6
                ) { Text(stringResource(R.string.followup_hour_earlier)) }
                Text(
                    stringResource(R.string.followup_reminder_hour, hour),
                    Modifier.padding(top = 12.dp)
                )
                TextButton(
                    onClick = {
                        scope.launch { vm.settings.setReminderHour(hour + 1) }
                    },
                    enabled =
                    hour < 19
                ) { Text(stringResource(R.string.followup_hour_later)) }
            }
        }
        if (denied) {
            Text(stringResource(R.string.followup_permission_denied))
            TextButton(onClick = {
                context.startActivity(
                    if (Build.VERSION.SDK_INT >=
                        26
                    ) {
                        Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    } else {
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                    }
                )
            }) { Text(stringResource(R.string.followup_settings)) }
        }
    }
}
