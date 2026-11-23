package com.idontremember.personalcrm.ui.followups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.entity.FollowUp
import com.idontremember.personalcrm.data.recovery.TrashKind
import com.idontremember.personalcrm.ui.components.ErrorState
import com.idontremember.personalcrm.ui.components.LoadingState
import com.idontremember.personalcrm.ui.components.exposeTestTags
import com.idontremember.personalcrm.ui.components.formatEventDateText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpReader(
    existingId: Long,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onContact: (Long) -> Unit = {},
    vm: FollowUpViewModel = hiltViewModel()
) {
    var item by remember(existingId) { mutableStateOf<FollowUp?>(null) }
    var loaded by remember(existingId) { mutableStateOf(false) }
    var error by remember(existingId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val people by vm.people.collectAsStateWithLifecycle()
    val dates by vm.dateDisplay.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.followup_deleted)
    val undo = stringResource(R.string.followup_undo)
    LaunchedEffect(existingId) {
        focus.clearFocus(force = true)
        keyboard?.hide()
        try {
            item = vm.repository.get(existingId)
        } catch (
            e: CancellationException
        ) {
            throw e
        } catch (_: Exception) {
            error = true
        }
        loaded = true
    }
    ModalBottomSheet(
        modifier = modifier.exposeTestTags(),
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(
                max = 600.dp
            ).padding(horizontal = 16.dp).testTag("followup_reader")
        ) {
            Row(Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.EventAvailable,
                    null,
                    modifier = Modifier.padding(top = 12.dp, end = 8.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    stringResource(R.string.recall_followup),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(top = 12.dp)
                )
                if (item != null) {
                    IconButton(enabled = !busy, onClick = {
                        onEdit(existingId)
                    }, modifier = Modifier.testTag("followup_reader_edit")) {
                        Icon(Icons.Default.Edit, stringResource(R.string.reading_edit_followup))
                    }
                    IconButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                vm.recovery.delete(TrashKind.FOLLOW_UP, existingId)
                                if (snackbar.showSnackbar(
                                        deleted,
                                        undo,
                                        duration = SnackbarDuration.Short
                                    ) ==
                                    SnackbarResult.ActionPerformed
                                ) {
                                    vm.recovery.restore(TrashKind.FOLLOW_UP, existingId)
                                    busy = false
                                } else {
                                    onDismiss()
                                }
                            } catch (
                                e: CancellationException
                            ) {
                                throw e
                            } catch (_: Exception) {
                                error = true
                                busy = false
                            }
                        }
                    }, modifier = Modifier.testTag("followup_reader_delete")) {
                        Icon(Icons.Default.Delete, stringResource(R.string.followup_delete))
                    }
                }
                IconButton(
                    enabled = !busy,
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_followup_reader")
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.reading_close))
                }
            }
            Column(
                Modifier.weight(
                    1f,
                    fill = false
                ).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)
            ) {
                when {
                    !loaded -> LoadingState(testTag = "followup_reader_loading")
                    item == null -> ErrorState(
                        stringResource(R.string.followup_unavailable),
                        testTag = "followup_reader_unavailable"
                    )
                    else -> item?.let { record ->
                        Text(
                            record.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().testTag("followup_reader_text")
                        )
                        Spacer(Modifier.height(12.dp))
                        FollowUpStatus(record.dueDate, record.completedAt)
                        Text(
                            stringResource(
                                R.string.reading_created_date,
                                formatEventDateText(record.createdAt, dates)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(
                                top = 12.dp
                            ).testTag("followup_reader_created")
                        )
                        TextButton(onClick = {
                            onContact(record.contactId)
                        }, enabled = !busy, modifier = Modifier.testTag("followup_reader_person")) {
                            Text(
                                people.find {
                                    it.id ==
                                        record.contactId
                                }?.displayName.orEmpty()
                            )
                        }
                        Button(
                            enabled = !busy,
                            modifier = Modifier.testTag(
                                "followup_reader_complete"
                            ),
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        vm.repository.complete(
                                            existingId,
                                            record.completedAt == null
                                        )
                                        item =
                                            vm.repository.get(existingId)
                                        busy = false
                                    } catch (
                                        e: CancellationException
                                    ) {
                                        throw e
                                    } catch (
                                        _: Exception
                                    ) {
                                        error = true
                                        busy = false
                                    }
                                }
                            }
                        ) {
                            Text(
                                stringResource(
                                    if (record.completedAt ==
                                        null
                                    ) {
                                        R.string.followup_done
                                    } else {
                                        R.string.followup_reopen
                                    }
                                )
                            )
                        }
                    }
                }
                if (error) {
                    Text(
                        stringResource(R.string.followup_error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            SnackbarHost(snackbar)
            Spacer(Modifier.height(16.dp))
        }
    }
}
