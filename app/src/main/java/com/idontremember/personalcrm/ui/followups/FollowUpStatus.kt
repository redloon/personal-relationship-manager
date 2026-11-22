package com.idontremember.personalcrm.ui.followups

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.idontremember.personalcrm.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FollowUpStatus(dueDate: String?, completedAt: Long?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(dueDate?.let { stringResource(R.string.reading_planned_date, formatFollowUpDate(it)) }
            ?: stringResource(R.string.followup_no_date))
        if (completedAt != null) Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.followup_completed), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
