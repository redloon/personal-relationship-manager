package com.idontremember.personalcrm.ui.recall

import com.idontremember.personalcrm.data.search.RecallKind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.search.RecallEntry
import com.idontremember.personalcrm.data.search.RecallSearch
import com.idontremember.personalcrm.ui.components.formatEventDateText
import com.idontremember.personalcrm.ui.followups.FollowUpStatus
import com.idontremember.personalcrm.ui.reading.RecordPreview

@Composable
fun RecallNoteRow(row: RecallEntry, query: String, dateDisplay: DateDisplay = DateDisplay.ABSOLUTE, onClick: () -> Unit) {
    val kind = stringResource(when (row.kind) { RecallKind.FACT -> R.string.recall_fact; RecallKind.FOLLOW_UP -> R.string.recall_followup; else -> R.string.recall_event })
    val excerpt = RecallSearch.excerpt(row.text, query)
    val source = row.text.replace(Regex("\\s+"), " ").trim()
    ListItem(
        headlineContent = { RecordPreview(excerpt, onClick, sourceTruncated = excerpt != source) },
        overlineContent = { Text(listOf(kind, row.title).filter { it.isNotBlank() }.joinToString(" · ")) },
        supportingContent = {
            if (row.kind == RecallKind.FOLLOW_UP) FollowUpStatus(row.dueDate, row.completedAt)
            else {
                val date = formatEventDateText(row.date, dateDisplay)
                Text(if (row.kind == RecallKind.FACT) stringResource(R.string.pilot_recorded, date) else date)
            }
        },
        leadingContent = if (row.kind == RecallKind.FOLLOW_UP) { { Icon(Icons.Default.EventAvailable, null, tint = MaterialTheme.colorScheme.tertiary) } } else null,
        colors = if (row.kind == RecallKind.FOLLOW_UP) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .35f)) else ListItemDefaults.colors(),
        modifier = Modifier.fillMaxWidth().testTag("recall_${row.kind}_${row.id}").clickable(onClick = onClick)
    )
}
