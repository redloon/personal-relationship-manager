package com.idontremember.personalcrm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.idontremember.personalcrm.R

/**
 * Shared error state (P2). Used for failed loads and gone-missing content so
 * every screen reports failures the same way.
 *
 * @param message localized copy from `strings.xml` — never a string literal.
 * @param onRetry optional retry action; renders a Retry button (48dp touch target)
 *   when provided.
 * @param testTag snake_case tag for UI tests (default `error_state`); pass a
 *   screen-specific tag and add it to README's Test Tags list.
 * @param retryTestTag tag for the retry button (default `error_retry_button`).
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    testTag: String = "error_state",
    retryTestTag: String = "error_retry_button",
    retryLabel: String? = null
) {
    val resolvedRetryLabel = retryLabel ?: stringResource(R.string.action_retry)
    Column(
        modifier = modifier
            .testTag(testTag)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            // Announce errors politely when they appear (a11y baseline).
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    // a11y: keep the touch target at least 48dp.
                    .heightIn(min = 48.dp)
                    .testTag(retryTestTag)
            ) {
                Text(resolvedRetryLabel)
            }
        }
    }
}
