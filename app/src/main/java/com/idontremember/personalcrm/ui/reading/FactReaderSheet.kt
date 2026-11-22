package com.idontremember.personalcrm.ui.reading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.recovery.TrashKind
import com.idontremember.personalcrm.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactReaderSheet(
    factId: Long,
    onDismiss: () -> Unit,
    onContact: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    vm: FactReaderViewModel = hiltViewModel()
) {
    var fact by remember(factId) { mutableStateOf<ContactFact?>(null) }
    var personName by remember(factId) { mutableStateOf("") }
    var loaded by remember(factId) { mutableStateOf(false) }
    var failed by remember(factId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val dates by vm.dateDisplay.collectAsState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.recall_fact_deleted)
    val undo = stringResource(R.string.recall_undo)
    LaunchedEffect(factId) {
        focus.clearFocus(force = true)
        keyboard?.hide()
        try {
            fact = vm.facts.getById(factId)
            personName = fact?.let { vm.contacts.getContactById(it.contactId)?.displayName }.orEmpty()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        loaded = true
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.exposeTestTags(), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(horizontal = 16.dp).testTag("fact_reader")) {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reading_fact_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(top = 12.dp))
                if (fact != null) {
                    IconButton(enabled = !busy, onClick = { onEdit(factId) }, modifier = Modifier.testTag("fact_reader_edit")) { Icon(Icons.Default.Edit, stringResource(R.string.recall_edit_fact)) }
                    IconButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                vm.recovery.delete(TrashKind.FACT, factId)
                                if (snackbar.showSnackbar(deleted, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                                    vm.recovery.restore(TrashKind.FACT, factId)
                                    busy = false
                                } else onDismiss()
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { failed = true; busy = false }
                        }
                    }, modifier = Modifier.testTag("delete_fact_$factId")) { Icon(Icons.Default.Delete, stringResource(R.string.cd_delete_fact)) }
                }
                IconButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.testTag("close_fact_reader")) { Icon(Icons.Default.Close, stringResource(R.string.reading_close)) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                when {
                    !loaded -> LoadingState(testTag = "fact_reader_loading")
                    fact == null -> ErrorState(stringResource(R.string.reading_fact_unavailable), testTag = "fact_reader_unavailable")
                    else -> fact?.let { item ->
                        Text(item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().testTag("fact_reader_text"))
                        Text(stringResource(R.string.pilot_recorded, formatEventDateText(item.createdAt, dates)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp))
                        TextButton(onClick = { onContact(item.contactId) }, enabled = !busy, modifier = Modifier.testTag("fact_reader_person")) { Text(personName) }
                    }
                }
                if (failed) Text(stringResource(R.string.recall_action_failed), color = MaterialTheme.colorScheme.error)
            }
            SnackbarHost(snackbar)
            Spacer(Modifier.height(16.dp))
        }
    }
}
