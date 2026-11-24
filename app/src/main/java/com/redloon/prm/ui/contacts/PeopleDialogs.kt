package com.redloon.prm.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.redloon.prm.R
import com.redloon.prm.data.contacts.PeopleDeletionPreview
import com.redloon.prm.data.contacts.PeopleSort
import com.redloon.prm.ui.components.exposeTestTags

internal fun PeopleSort.label(): Int = when (this) {
    PeopleSort.NAME_ASC -> R.string.people_sort_name_asc
    PeopleSort.NAME_DESC -> R.string.people_sort_name_desc
    PeopleSort.RECENT -> R.string.people_sort_recent
    PeopleSort.LONGEST_AGO -> R.string.people_sort_longest_ago
    PeopleSort.ADDED_NEWEST -> R.string.people_sort_added_newest
    PeopleSort.ADDED_OLDEST -> R.string.people_sort_added_oldest
    PeopleSort.MOST_INTERACTIONS -> R.string.people_sort_most
    PeopleSort.FEWEST_INTERACTIONS -> R.string.people_sort_fewest
}

@Composable
internal fun PeopleSortDialog(
    selected: PeopleSort,
    onSelect: (PeopleSort) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.exposeTestTags().testTag("people_sort_dialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.people_sort_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                PeopleSort.entries.forEach { sort ->
                    TextButton(onClick = {
                        onSelect(sort)
                    }, modifier = Modifier.fillMaxWidth().testTag("people_sort_${sort.name}")) {
                        RadioButton(selected = sort == selected, onClick = null)
                        Text(stringResource(sort.label()), Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
internal fun PeopleDeletionDialog(
    preview: PeopleDeletionPreview,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.exposeTestTags().testTag("people_delete_dialog"),
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                androidx.compose.ui.res.pluralStringResource(
                    R.plurals.people_delete_title,
                    preview.people.size,
                    preview.people.size
                )
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(preview.people.take(5).joinToString(", ") { it.name })
                if (preview.people.size >
                    5
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.people_delete_more_names,
                            preview.people.size - 5,
                            preview.people.size - 5
                        )
                    )
                }
                Text(
                    stringResource(
                        R.string.people_delete_counts,
                        preview.factCount,
                        preview.followUpCount,
                        preview.relationshipCount,
                        preview.eventCount
                    )
                )
                Text(stringResource(R.string.people_delete_permanent))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
                modifier = Modifier.testTag("confirm_people_delete"),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.people_delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.testTag("cancel_people_delete")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
