package com.idontremember.personalcrm.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared empty-state block (P2). Every empty list or section renders through this
 * composable so empty states look, read, and test the same everywhere.
 *
 * @param message localized copy from `strings.xml` — never a string literal.
 * @param testTag snake_case tag for UI tests; pass a screen-specific tag and add it
 *   to README's Test Tags list (the default is `empty_state`).
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    testTag: String = "empty_state"
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
