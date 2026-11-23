package com.idontremember.personalcrm.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.recovery.TrashKind
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupRecoverySection(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var folderOpenFailed by remember { mutableStateOf(false) }
    val status by viewModel.backupStatus.collectAsState()
    val busy by viewModel.backupBusy.collectAsState()
    val deleted by viewModel.deletedItems.collectAsState()
    val recoveryFailed by viewModel.recoveryFailed.collectAsState()
    var showDeleted by rememberSaveable { mutableStateOf(false) }
    val folderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) viewModel.setBackupFolder(uri)
        }
    SettingsSection(
        stringResource(R.string.reliability_daily_backup),
        testTag = "automatic_backup_section",
        modifier = modifier
    ) {
        Text(
            stringResource(R.string.reliability_backup_description),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        SettingsActionRow(
            stringResource(
                if (status.folder ==
                    null
                ) {
                    R.string.reliability_choose_folder
                } else {
                    R.string.reliability_change_folder
                }
            ),
            onClick = {
                folderLauncher.launch(null)
            },
            enabled = !busy,
            testTag = "choose_backup_folder"
        )
        if (status.folder != null) {
            SettingsActionRow(stringResource(R.string.backup_open_folder), onClick = {
                folderOpenFailed = !openBackupFolder(status.folder!!) { context.startActivity(it) }
            }, testTag = "open_backup_folder")
            if (folderOpenFailed) {
                Text(
                    stringResource(R.string.backup_open_folder_failed),
                    modifier = Modifier.padding(
                        horizontal = 16.dp
                    ).testTag("backup_folder_open_error"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val last = status.lastSuccess
            Text(
                if (last ==
                    null
                ) {
                    stringResource(R.string.reliability_backup_never)
                } else {
                    stringResource(
                        R.string.reliability_last_backup,
                        DateFormat.getDateTimeInstance(
                            DateFormat.MEDIUM,
                            DateFormat.SHORT
                        ).format(Date(last))
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall
            )
            SettingsActionRow(
                stringResource(
                    if (busy) R.string.reliability_backing_up else R.string.reliability_backup_now
                ),
                onClick = viewModel::backUpNow,
                enabled = !busy,
                testTag = "backup_now"
            )
            SettingsActionRow(
                stringResource(R.string.reliability_disable_backup),
                onClick = {
                    viewModel.setBackupFolder(null)
                },
                enabled = !busy,
                testTag = "disable_automatic_backup"
            )
        }
        if (status.failed) {
            Text(
                stringResource(R.string.reliability_backup_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp).testTag("automatic_backup_error")
            )
        }
        Text(
            stringResource(R.string.reliability_portable_backup),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp)
        )
    }
    SettingsSection(
        stringResource(R.string.reliability_recently_deleted),
        testTag = "recently_deleted_section"
    ) {
        SettingsActionRow(
            stringResource(R.string.reliability_recently_deleted),
            subtitle = stringResource(R.string.reliability_trash_description),
            onClick = { showDeleted = !showDeleted },
            testTag = "show_recently_deleted"
        )
        if (showDeleted) {
            if (deleted.isEmpty()) {
                Text(
                    stringResource(R.string.reliability_trash_empty),
                    modifier = Modifier.padding(16.dp)
                )
            }
            deleted.forEach { item ->
                ListItem(headlineContent = { Text(item.text, maxLines = 3) }, supportingContent = {
                    Text(
                        stringResource(
                            when (item.kind) {
                                TrashKind.EVENT -> R.string.reliability_trash_note
                                TrashKind.FACT -> R.string.reliability_trash_fact
                                TrashKind.FOLLOW_UP -> R.string.reliability_trash_follow_up
                            }
                        )
                    )
                }, trailingContent = {
                    TextButton(
                        onClick = { viewModel.recover(item.kind, item.id) },
                        modifier = Modifier.testTag(
                            "recover_${item.kind.name.lowercase()}_${item.id}"
                        )
                    ) {
                        Text(stringResource(R.string.reliability_recover))
                    }
                })
            }
        }
        if (recoveryFailed) {
            TextButton(onClick = viewModel::clearRecoveryError) {
                Text(stringResource(R.string.reliability_recovery_failed))
            }
        }
    }
}
