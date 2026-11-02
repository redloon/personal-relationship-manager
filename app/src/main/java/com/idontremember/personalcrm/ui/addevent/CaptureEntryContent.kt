package com.idontremember.personalcrm.ui.addevent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idontremember.personalcrm.ui.components.exposeTestTags
import com.idontremember.personalcrm.data.settings.SettingsRepository
import com.idontremember.personalcrm.data.settings.ThemeMode
import com.idontremember.personalcrm.ui.theme.PersonalCRMTheme

/** Tile and share capture honor the same appearance settings as the main app. */
@Composable
fun CaptureEntryContent(settings: SettingsRepository, initialText: String? = null, onClose: () -> Unit) {
    val theme by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    PersonalCRMTheme(darkTheme = dark, dynamicColor = dynamicColor) {
        Surface(modifier = Modifier.fillMaxSize().exposeTestTags(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)) {
            AddEventSheet(onDismiss = onClose, onSaved = onClose, initialText = initialText)
        }
    }
}
