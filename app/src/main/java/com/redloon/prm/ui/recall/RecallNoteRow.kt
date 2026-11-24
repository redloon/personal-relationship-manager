package com.redloon.prm.ui.recall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.redloon.prm.R
import com.redloon.prm.data.search.RecallEntry
import com.redloon.prm.data.search.RecallKind
import com.redloon.prm.data.search.RecallSearch
import com.redloon.prm.data.settings.DateDisplay
import com.redloon.prm.ui.components.formatEventDateText
import com.redloon.prm.ui.followups.FollowUpStatus
import com.redloon.prm.ui.reading.RecordPreview

@Composable
fun RecallNoteRow(
    row: RecallEntry,
    query: String,
    modifier: Modifier = Modifier,
    dateDisplay: DateDisplay = DateDisplay.ABSOLUTE,
    onClick: () -> Unit
) {
    val kind =
        stringResource(
            when (row.kind) {
                RecallKind.FACT -> R.string.recall_fact
                RecallKind.FOLLOW_UP -> R.string.recall_followup
                else -> R.string.recall_event
            }
        )
    val excerpt = RecallSearch.excerpt(row.text, query)
    val source = row.text.replace(Regex("\\s+"), " ").trim()
    ListItem(
        headlineContent = { RecordPreview(excerpt, onClick, sourceTruncated = excerpt != source) },
        overlineContent = {
            Text(listOf(kind, row.title).filter { it.isNotBlank() }.joinToString(" · "))
        },
        supportingContent = {
            if (row.kind == RecallKind.FOLLOW_UP) {
                FollowUpStatus(row.dueDate, row.completedAt)
            } else {
                val date = formatEventDateText(row.date, dateDisplay)
                Text(
                    if (row.kind ==
                        RecallKind.FACT
                    ) {
                        stringResource(R.string.pilot_recorded, date)
                    } else {
                        date
                    }
                )
            }
        },
        leadingContent = if (row.kind ==
            RecallKind.FOLLOW_UP
        ) {
            {
                Icon(
                    Icons.Default.EventAvailable,
                    null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        } else {
            null
        },
        colors = if (row.kind ==
            RecallKind.FOLLOW_UP
        ) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .35f)
            )
        } else {
            ListItemDefaults.colors()
        },
        modifier = modifier.fillMaxWidth().testTag(
            "recall_${row.kind}_${row.id}"
        ).clickable(onClick = onClick)
    )
}
