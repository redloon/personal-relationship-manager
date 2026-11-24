package com.redloon.prm.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.redloon.prm.R

/** A preview must say when there is more to read, including pre-shortened search excerpts. */
@Composable
fun RecordPreview(
    text: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    sourceTruncated: Boolean = false
) {
    var overflows by remember(text) { mutableStateOf(false) }
    Column(modifier) {
        Text(
            text,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                overflows =
                    result.hasVisualOverflow ||
                    (0 until result.lineCount).any { result.isLineEllipsized(it) }
            }
        )
        if (sourceTruncated || overflows) {
            TextButton(onClick = onOpen) {
                Text(
                    stringResource(R.string.reading_read_more),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
