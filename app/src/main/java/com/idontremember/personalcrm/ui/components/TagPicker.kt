package com.idontremember.personalcrm.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.entity.Tag

/** Callers own membership and creation permissions; the search/list interaction is shared. */
@Composable
fun TagPicker(
    tags: List<Tag>,
    query: String,
    onQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Tag) -> Unit,
    onCreate: ((String) -> Unit)? = null,
    capture: Boolean = false
) {
    AlertDialog(
        modifier = Modifier.exposeTestTags(),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (capture) R.string.capture_tags else R.string.pilot_add_tag))
        },
        text = {
            Column {
                SearchField(
                    query,
                    onQuery,
                    stringResource(
                        if (capture) R.string.capture_tag_hint else R.string.recall_tag_search
                    ),
                    if (capture) "clear_capture_tag_search" else "clear_tag_picker_search",
                    Modifier.fillMaxWidth().then(
                        if (capture) Modifier.testTag("capture_tag_search") else Modifier
                    )
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(
                        tags.filter {
                            it.name.contains(query.trim(), true)
                        },
                        key = { it.id }
                    ) { tag ->
                        TextButton(onClick = {
                            onSelect(tag)
                        }, modifier = Modifier.fillMaxWidth()) { Text(tag.name) }
                    }
                }
                val name = query.trim().removePrefix("#")
                if (onCreate != null &&
                    name.isNotBlank()
                ) {
                    TextButton(onClick = { onCreate(name) }) {
                        Text(stringResource(R.string.capture_create_tag, name))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pilot_done)) }
        }
    )
}
