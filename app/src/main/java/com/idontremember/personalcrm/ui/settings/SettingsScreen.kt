package com.idontremember.personalcrm.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.settings.DefaultEventTime
import com.idontremember.personalcrm.data.settings.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val exportState by viewModel.exportState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val restorePreview by viewModel.restorePreview.collectAsState()
    val exportSuccessMessage = stringResource(R.string.settings_export_success)
    val exportErrorMessage = stringResource(R.string.settings_export_error)
    val restoreSuccessMessage = stringResource(R.string.settings_restore_success)
    val restoreErrorMessage = stringResource(R.string.settings_restore_error)

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportTo(context.contentResolver, uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.readImport(context.contentResolver, uri) }

    LaunchedEffect(exportState, restoreState) {
        when (exportState) {
            ExportState.DONE -> {
                snackbarHostState.showSnackbar(exportSuccessMessage)
                viewModel.clearExportFeedback()
            }
            ExportState.ERROR -> {
                snackbarHostState.showSnackbar(exportErrorMessage)
                viewModel.clearExportFeedback()
            }
            else -> {}
        }
        when (restoreState) {
            RestoreState.DONE -> {
                snackbarHostState.showSnackbar(restoreSuccessMessage)
                viewModel.clearRestoreFeedback()
            }
            RestoreState.ERROR -> {
                snackbarHostState.showSnackbar(restoreErrorMessage)
                viewModel.clearRestoreFeedback()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.testTag("settings_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
            SettingsSection(
                title = stringResource(R.string.settings_section_appearance),
                testTag = "settings_section_appearance"
            ) {
                SettingsChoiceGroup(
                    title = stringResource(R.string.settings_pref_theme),
                    groupTag = "theme_mode_selector",
                    options = listOf(
                        SettingsChoiceOption(
                            label = stringResource(R.string.settings_theme_system),
                            tag = "theme_mode_system",
                            selected = themeMode == ThemeMode.SYSTEM,
                            onSelect = { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                        ),
                        SettingsChoiceOption(
                            label = stringResource(R.string.settings_theme_light),
                            tag = "theme_mode_light",
                            selected = themeMode == ThemeMode.LIGHT,
                            onSelect = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                        ),
                        SettingsChoiceOption(
                            label = stringResource(R.string.settings_theme_dark),
                            tag = "theme_mode_dark",
                            selected = themeMode == ThemeMode.DARK,
                            onSelect = { viewModel.setThemeMode(ThemeMode.DARK) }
                        )
                    )
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_pref_dynamic_color),
                    checked = dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                    testTag = "dynamic_color_toggle"
                )
            }

            SettingsSection(
                title = stringResource(R.string.settings_section_date_time),
                testTag = "settings_section_date_time"
            ) {
                SettingsChoiceGroup(
                    title = stringResource(R.string.settings_pref_date_display),
                    groupTag = "date_display_selector",
                    options = listOf(
                        SettingsChoiceOption(
                            label = stringResource(R.string.settings_date_display_absolute),
                            tag = "date_display_absolute",
                            selected = dateDisplay == DateDisplay.ABSOLUTE,
                            onSelect = { viewModel.setDateDisplay(DateDisplay.ABSOLUTE) }
                        ),
                        SettingsChoiceOption(
                            label = stringResource(R.string.settings_date_display_relative),
                            tag = "date_display_relative",
                            selected = dateDisplay == DateDisplay.RELATIVE,
                            onSelect = { viewModel.setDateDisplay(DateDisplay.RELATIVE) }
                        )
                    )
                )
                SettingsChoiceGroup(
                    title = stringResource(R.string.settings_pref_default_event_time),
                    groupTag = "default_event_time_selector",
                    options = listOf(
                        SettingsChoiceOption(
                            label = stringResource(R.string.settings_default_event_time_now),
                            tag = "default_event_time_now",
                            selected = defaultEventTime == DefaultEventTime.NOW,
                            onSelect = { viewModel.setDefaultEventTime(DefaultEventTime.NOW) }
                        ),
                        SettingsChoiceOption(
                            label = stringResource(R.string.settings_default_event_time_last_used),
                            tag = "default_event_time_last_used",
                            selected = defaultEventTime == DefaultEventTime.LAST_USED,
                            onSelect = { viewModel.setDefaultEventTime(DefaultEventTime.LAST_USED) }
                        )
                    )
                )
            }

            SettingsSection(
                title = stringResource(R.string.settings_section_data),
                testTag = "settings_section_data"
            ) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_export_json),
                    subtitle = stringResource(R.string.settings_export_json_desc),
                    onClick = {
                        val ts = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
                        safLauncher.launch("personal-crm-backup-$ts.json")
                    },
                    enabled = exportState != ExportState.EXPORTING,
                    testTag = "export_json_button"
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_import_json),
                    subtitle = stringResource(R.string.settings_import_json_desc),
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    enabled = restoreState != RestoreState.RESTORING && restoreState != RestoreState.LOADING,
                    testTag = "import_json_button"
                )
            }
        }
    }

    val preview = restorePreview
    if (restoreState == RestoreState.READY && preview != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_body,
                preview.contacts.size, preview.facts.size, preview.events.size, preview.tags.size)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore, modifier = Modifier.testTag("confirm_restore_button")) {
                    Text(stringResource(R.string.settings_restore_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) }
            },
            modifier = Modifier.testTag("restore_confirmation_dialog")
        )
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

/** Clickable row for one-shot actions (not preferences). */
@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
