package com.idontremember.personalcrm.ui.addevent

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.idontremember.personalcrm.R

@Composable
internal fun CaptureHeader(
    title: String, saving: Boolean, saveEnabled: Boolean, editing: Boolean,
    onClose: () -> Unit, onSave: () -> Unit, onSuggestionWidth: (Dp) -> Unit
) {
    val density = LocalDensity.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Suggestions may cover the title, but must leave Close and Save available.
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).onSizeChanged {
            onSuggestionWidth(with(density) { it.width.toDp().coerceAtLeast(1.dp) })
        })
        IconButton(onClick = onClose, enabled = !saving) { Icon(Icons.Default.Close, stringResource(R.string.cd_close)) }
        Button(onClick = onSave, enabled = saveEnabled,
            modifier = Modifier.testTag(if (editing) "save_event_edit_button" else "save_event_button")) {
            Text(stringResource(if (saving) R.string.capture_saving else R.string.capture_save))
        }
    }
}
