package com.redloon.prm.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.redloon.prm.R
import com.redloon.prm.data.db.entity.ContactFact
import com.redloon.prm.data.db.entity.Event
import com.redloon.prm.data.settings.DateDisplay
import com.redloon.prm.ui.components.exposeTestTags
import com.redloon.prm.ui.components.formatEventDateText
import com.redloon.prm.ui.reading.RecordPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileRecordsSheet(
    title: String,
    kind: String,
    onDismiss: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.exposeTestTags().testTag("profile_${kind}_sheet")
    ) {
        Column(Modifier.fillMaxHeight(.85f).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.reading_close)) }
            }
            LazyColumn(Modifier.weight(1f).testTag("profile_${kind}_list"), content = content)
        }
    }
}

@Composable
internal fun ProfileFactRow(fact: ContactFact, dateDisplay: DateDisplay, onOpen: () -> Unit) {
    ListItem(
        headlineContent = { RecordPreview(fact.text, onOpen = onOpen) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.pilot_recorded,
                    formatEventDateText(fact.createdAt, dateDisplay)
                )
            )
        },
        modifier = Modifier.fillMaxWidth().testTag(
            "fact_row_${fact.id}"
        ).clickable(onClick = onOpen)
    )
}

@Composable
internal fun ProfileNoteRow(event: Event, dateDisplay: DateDisplay, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(
            bottom = 8.dp
        ).testTag("event_preview_${event.id}")
    ) {
        Column(Modifier.padding(16.dp)) {
            RecordPreview(event.note, onOpen = onOpen)
            Text(
                formatEventDateText(event.eventDate, dateDisplay),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
