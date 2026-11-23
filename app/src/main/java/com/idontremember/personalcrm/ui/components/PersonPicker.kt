package com.idontremember.personalcrm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.capture.ContactNames
import com.idontremember.personalcrm.data.capture.PersonChoice
import com.idontremember.personalcrm.data.capture.TagChoice
import com.idontremember.personalcrm.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One picker/create flow for note, fact, follow-up and People entry points. */
@Composable
fun PersonPicker(
    onDismiss: () -> Unit,
    onPersonSelected: (PersonChoice) -> Unit,
    selectedIds: Set<Long> = emptySet(),
    initialQuery: String = "",
    startCreating: Boolean = false,
    viewModel: PersonPickerViewModel = hiltViewModel()
) {
    val people by viewModel.contacts.collectAsState()
    val creating by viewModel.creating.collectAsState()
    val failed by viewModel.failed.collectAsState()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var name by rememberSaveable { mutableStateOf(initialQuery.trim()) }
    var createMode by rememberSaveable { mutableStateOf(startCreating) }
    val matching = if (createMode) {
        people.filter {
            ContactNames.key(it.displayName) ==
                ContactNames.key(name)
        }
    } else {
        people.filter { it.id !in selectedIds && it.displayName.contains(query.trim(), true) }
    }
    AlertDialog(
        modifier = Modifier.exposeTestTags().testTag("person_picker"),
        onDismissRequest = { if (!creating) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (createMode) R.string.usability_new_person else R.string.capture_people
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (createMode) {
                    OutlinedTextField(
                        name,
                        {
                            name = it
                        },
                        label = { Text(stringResource(R.string.usability_full_name)) },
                        singleLine = true,
                        enabled = !creating,
                        modifier = Modifier.fillMaxWidth().testTag("person_name_input")
                    )
                } else {
                    SearchField(
                        query,
                        {
                            query = it
                        },
                        stringResource(
                            R.string.capture_people_hint
                        ),
                        "clear_capture_person_search",
                        Modifier.fillMaxWidth().testTag(
                            "event_person_search"
                        )
                    )
                }
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(matching, key = { it.id }) { person ->
                        TextButton(
                            onClick = {
                                onPersonSelected(
                                    PersonChoice(person.id, person.displayName, person.phoneNumber)
                                )
                            },
                            enabled = !creating,
                            modifier = Modifier.fillMaxWidth().testTag(
                                "event_person_option_${person.id}"
                            )
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    if (createMode) {
                                        stringResource(
                                            R.string.usability_use_existing,
                                            person.displayName
                                        )
                                    } else {
                                        person.displayName
                                    }
                                )
                                Text(
                                    person.phoneNumber ?: person.email
                                        ?: stringResource(
                                            R.string.usability_person_saved,
                                            DateFormat.getDateTimeInstance(
                                                DateFormat.SHORT,
                                                DateFormat.SHORT
                                            ).format(Date(person.createdAt))
                                        ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                if (!createMode && people.isEmpty() &&
                    query.isBlank()
                ) {
                    Text(stringResource(R.string.capture_empty_people))
                }
                if (!createMode &&
                    query.isNotBlank()
                ) {
                    TextButton(onClick = {
                        name = query.trim()
                        createMode =
                            true
                    }, modifier = Modifier.testTag("capture_create_person")) {
                        Text(stringResource(R.string.capture_create_person, query.trim()))
                    }
                }
                if (failed) {
                    Text(
                        stringResource(R.string.usability_create_failed),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (createMode) {
                TextButton(
                    onClick = {
                        viewModel.create(name, matching.isNotEmpty(), onPersonSelected)
                    },
                    enabled =
                    name.isNotBlank() && !creating,
                    modifier = Modifier.testTag("create_person_button")
                ) {
                    Text(
                        stringResource(
                            if (matching.isEmpty()) {
                                R.string.usability_create_person
                            } else {
                                R.string.usability_different_person
                            }
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@HiltViewModel
class PersonPickerViewModel @Inject constructor(private val repository: ContactRepository) :
    ViewModel() {
    val contacts = repository.getAllContacts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val creating = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    fun create(name: String, allowDuplicate: Boolean, onCreated: (PersonChoice) -> Unit) {
        if (name.isBlank() || creating.value) return
        creating.value = true
        failed.value = false
        viewModelScope.launch {
            try {
                val id = repository.createLocalContact(name, allowDuplicate)
                val person = repository.getContactById(id) ?: error("Person unavailable")
                onCreated(PersonChoice(id, person.displayName, person.phoneNumber))
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (
                _: Exception
            ) {
                failed.value = true
            } finally {
                creating.value = false
            }
        }
    }
}
