package com.idontremember.personalcrm.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.settings.DefaultEventTime
import com.idontremember.personalcrm.data.settings.ThemeMode

/**
 * Settings screen ([SettingsRoute] target). Grouped sections keep it trivially
 * extensible: later packets (P2, P10, P16/About) add a [SettingsSection] with
 * [SettingsChoiceGroup] / [SettingsSwitchRow] rows instead of inventing new layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val dateDisplay by viewModel.dateDisplay.collectAsState()
    val defaultEventTime by viewModel.defaultEventTime.collectAsState()

    Scaffold(
        modifier = Modifier.testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = "Appearance", testTag = "settings_section_appearance") {
                SettingsChoiceGroup(
                    title = "Theme",
                    groupTag = "theme_mode_selector",
                    options = listOf(
                        SettingsChoiceOption(
                            label = "System",
                            tag = "theme_mode_system",
                            selected = themeMode == ThemeMode.SYSTEM,
                            onSelect = { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                        ),
                        SettingsChoiceOption(
                            label = "Light",
                            tag = "theme_mode_light",
                            selected = themeMode == ThemeMode.LIGHT,
                            onSelect = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                        ),
                        SettingsChoiceOption(
                            label = "Dark",
                            tag = "theme_mode_dark",
                            selected = themeMode == ThemeMode.DARK,
                            onSelect = { viewModel.setThemeMode(ThemeMode.DARK) }
                        )
                    )
                )
                SettingsSwitchRow(
                    title = "Dynamic color",
                    checked = dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                    testTag = "dynamic_color_toggle"
                )
            }

            SettingsSection(title = "Date & time", testTag = "settings_section_date_time") {
                SettingsChoiceGroup(
                    title = "Date display",
                    groupTag = "date_display_selector",
                    options = listOf(
                        SettingsChoiceOption(
                            label = "Absolute",
                            tag = "date_display_absolute",
                            selected = dateDisplay == DateDisplay.ABSOLUTE,
                            onSelect = { viewModel.setDateDisplay(DateDisplay.ABSOLUTE) }
                        ),
                        SettingsChoiceOption(
                            label = "Relative",
                            tag = "date_display_relative",
                            selected = dateDisplay == DateDisplay.RELATIVE,
                            onSelect = { viewModel.setDateDisplay(DateDisplay.RELATIVE) }
                        )
                    )
                )
                SettingsChoiceGroup(
                    title = "Default event time",
                    groupTag = "default_event_time_selector",
                    options = listOf(
                        SettingsChoiceOption(
                            label = "Now",
                            tag = "default_event_time_now",
                            selected = defaultEventTime == DefaultEventTime.NOW,
                            onSelect = { viewModel.setDefaultEventTime(DefaultEventTime.NOW) }
                        ),
                        SettingsChoiceOption(
                            label = "Last used",
                            tag = "default_event_time_last_used",
                            selected = defaultEventTime == DefaultEventTime.LAST_USED,
                            onSelect = { viewModel.setDefaultEventTime(DefaultEventTime.LAST_USED) }
                        )
                    )
                )
            }
        }
    }
}

/** One radio-style option in a [SettingsChoiceGroup]. */
data class SettingsChoiceOption(
    val label: String,
    val tag: String,
    val selected: Boolean,
    val onSelect: () -> Unit
)

/**
 * A titled group of settings rows. The whole screen is a column of these sections —
 * add a new one (e.g. "Notifications" in P10) by dropping another [SettingsSection]
 * into [SettingsScreen]'s column.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable () -> Unit
) {
    val sectionModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    Column(modifier = sectionModifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        content()
    }
}

/** Radio group for an enum-style preference. */
@Composable
fun SettingsChoiceGroup(
    title: String,
    groupTag: String,
    options: List<SettingsChoiceOption>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        Column(modifier = Modifier.selectableGroup().testTag(groupTag)) {
            options.forEach { option ->
                SettingsChoiceRow(option)
            }
        }
    }
}

@Composable
private fun SettingsChoiceRow(option: SettingsChoiceOption) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = option.selected,
                role = Role.RadioButton,
                onClick = option.onSelect
            )
            .testTag(option.tag)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = option.selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = option.label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Switch row for a boolean preference. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Switch
            )
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
