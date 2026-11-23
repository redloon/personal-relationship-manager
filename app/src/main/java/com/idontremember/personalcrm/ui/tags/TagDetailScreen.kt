package com.idontremember.personalcrm.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.search.RecallKind
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.ui.components.LoadingState
import com.idontremember.personalcrm.ui.recall.BackIcon
import com.idontremember.personalcrm.ui.recall.RecallNoteRow
import com.idontremember.personalcrm.ui.recall.RecallViewModel
import com.idontremember.personalcrm.ui.recall.SectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailScreen(
    tagId: Long,
    onBack: () -> Unit,
    onContact: (Long) -> Unit,
    onEvent: (Long) -> Unit,
    modifier: Modifier = Modifier,
    vm: TagManagementViewModel = hiltViewModel(),
    recall: RecallViewModel = hiltViewModel()
) {
    val tags by vm.tags.collectAsState()
    val tag = tags.find { it.id == tagId }
    val people by remember(tagId) { vm.peopleForTag(tagId) }.collectAsState(initial = null)
    val noteIds by remember(tagId) { vm.noteIdsForTag(tagId) }.collectAsState(initial = null)
    val entries by recall.entries.collectAsState()
    val dateDisplay by recall.dateDisplay.collectAsState()
    val notes = entries?.filter {
        it.kind == RecallKind.EVENT && it.id in noteIds.orEmpty()
    }?.sortedByDescending { it.date }
    Scaffold(modifier = modifier.testTag("tag_detail_screen"), topBar = {
        TopAppBar(title = {
            Text(tag?.name ?: stringResource(R.string.pilot_tags))
        }, navigationIcon = { BackIcon(onBack) })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (tag == null &&
                entries != null
            ) {
                item { Text(stringResource(R.string.search_tag_missing)) }
            }
            item { SectionLabel(stringResource(R.string.recall_people)) }
            when {
                people == null -> item { LoadingState() }
                people.orEmpty().isEmpty() -> item {
                    Text(stringResource(R.string.search_tag_people_empty))
                }
                else -> items(people.orEmpty(), key = { "person:${it.id}" }) { person ->
                    ListItem(
                        headlineContent = { Text(person.displayName) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    when {
                                        person.direct && person.viaNote -> R.string.search_tag_both
                                        person.direct -> R.string.search_tag_direct
                                        else -> R.string.search_tag_via_note
                                    }
                                )
                            )
                        },
                        modifier = Modifier.testTag("tag_person_${person.id}").clickable {
                            onContact(person.id)
                        }
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.search_notes_type)) }
            when {
                notes == null || noteIds == null -> item { LoadingState() }
                notes.isEmpty() -> item { Text(stringResource(R.string.search_tag_notes_empty)) }
                else -> items(notes, key = { it.key }) { row ->
                    Box(Modifier.testTag("tag_note_${row.id}")) {
                        RecallNoteRow(row, "", dateDisplay = dateDisplay, onClick = {
                            onEvent(row.id)
                        })
                    }
                }
            }
        }
    }
}
