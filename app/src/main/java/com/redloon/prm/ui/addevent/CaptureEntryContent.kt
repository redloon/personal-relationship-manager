package com.redloon.prm.ui.addevent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redloon.prm.data.settings.SettingsRepository
import com.redloon.prm.data.settings.ThemeMode
import com.redloon.prm.ui.components.exposeTestTags
import com.redloon.prm.ui.theme.PrmTheme

/** Tile and share capture honor the same appearance settings as the main app. */
@Composable
fun CaptureEntryContent(
    settings: SettingsRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialText: String? = null
) {
    val theme by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    PrmTheme(darkTheme = dark, dynamicColor = dynamicColor) {
        Surface(
            modifier = modifier.fillMaxSize().exposeTestTags(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            AddEventSheet(onDismiss = onClose, onSaved = onClose, initialText = initialText)
        }
    }
}
