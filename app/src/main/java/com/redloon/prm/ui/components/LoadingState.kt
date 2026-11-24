package com.redloon.prm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.redloon.prm.R

/**
 * Shared loading placeholder (P2) for content that is still being loaded.
 *
 * @param label announced and shown under the spinner; defaults to the localized
 *   "Loading…" copy. Pass a specific label (from `strings.xml`) when useful.
 * @param testTag snake_case tag for UI tests (default `loading_state`); pass a
 *   screen-specific tag and add it to README's Test Tags list.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    testTag: String = "loading_state",
    label: String? = null
) {
    val resolvedLabel = label ?: stringResource(R.string.state_loading)
    Column(
        modifier = modifier
            .testTag(testTag)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = resolvedLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
