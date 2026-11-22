package com.idontremember.personalcrm.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.capture.InlineToken
import kotlinx.coroutines.delay

/** Literal prose stays literal. Only an explicit choice inserts an associated token. */
@Composable
fun AutocompleteTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    contacts: List<AutocompleteContact>,
    tags: List<AutocompleteTag>,
    onContactSelected: (AutocompleteContact, Int, Int) -> Unit,
    onTagSelected: (AutocompleteTag, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    autoFocus: Boolean = false,
    enableTags: Boolean = true,
    enableContacts: Boolean = true,
    inlineTokens: List<InlineToken> = emptyList(),
    onCreateContact: ((String, Int, Int) -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) { delay(150); focusRequester.requestFocus(); keyboardController?.show() }
    }
    val cursor = value.selection.start
    val trigger = detectTrigger(value.text, cursor)?.takeIf { candidate ->
        (if (candidate.type == TriggerType.CONTACT) enableContacts else enableTags) &&
            inlineTokens.none { candidate.startIndex >= it.start && candidate.startIndex < it.endExclusive }
    }
    var dismissedTrigger by remember { mutableStateOf<Pair<Int, TriggerType>?>(null) }
    val triggerKey = trigger?.let { it.startIndex to it.type }
    LaunchedEffect(triggerKey) { if (triggerKey != dismissedTrigger) dismissedTrigger = null }
    val tokenBackground = MaterialTheme.colorScheme.secondaryContainer
    val tokenColor = MaterialTheme.colorScheme.onSecondaryContainer
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester).testTag("note_text_field"),
            placeholder = { Text(placeholder ?: stringResource(R.string.note_placeholder)) },
            minLines = 1,
            visualTransformation = VisualTransformation { source ->
                val annotated = buildAnnotatedString {
                    append(source)
                    inlineTokens.forEach { token ->
                        if (token.start >= 0 && token.endExclusive <= source.length && token.start < token.endExclusive)
                            addStyle(SpanStyle(background = tokenBackground, color = tokenColor, fontWeight = FontWeight.SemiBold), token.start, token.endExclusive)
                    }
                }
                TransformedText(annotated, OffsetMapping.Identity)
            }
        )
        if (trigger != null && triggerKey != dismissedTrigger) {
            val contactMatches = contacts.filter { it.displayName.contains(trigger.query.trim(), true) }.take(5)
            val tagMatches = tags.filter { it.name.contains(trigger.query, true) }.take(5)
            val canCreate = trigger.query.isNotBlank()
            val visible = if (trigger.type == TriggerType.CONTACT) contactMatches.isNotEmpty() || (canCreate && onCreateContact != null) else true
            DropdownMenu(expanded = visible, onDismissRequest = { dismissedTrigger = triggerKey },
                properties = PopupProperties(focusable = false), modifier = Modifier.heightIn(max = 220.dp)) {
                if (trigger.type == TriggerType.CONTACT) {
                    contactMatches.forEach { person ->
                        DropdownMenuItem(text = { Column { Text(person.displayName); person.phoneNumber?.let { Text(it, style = MaterialTheme.typography.bodySmall) } } },
                            onClick = { dismissedTrigger = triggerKey; onContactSelected(person, trigger.startIndex, cursor) })
                    }
                    if (canCreate && onCreateContact != null) DropdownMenuItem(
                        text = { Text(stringResource(R.string.pilot_create_contact, trigger.query.trim())) },
                        modifier = Modifier.testTag("inline_create_person"),
                        onClick = { dismissedTrigger = triggerKey; onCreateContact(trigger.query.trim(), trigger.startIndex, cursor) })
                } else {
                    tagMatches.forEach { tag -> DropdownMenuItem(text = { Text(stringResource(R.string.usability_tag_label, tag.name)) }, onClick = { dismissedTrigger = triggerKey; onTagSelected(tag, trigger.startIndex, cursor) }) }
                    if (canCreate && tagMatches.none { it.name.equals(trigger.query.trim(), true) }) DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_tag_option, trigger.query.trim())) },
                        onClick = { dismissedTrigger = triggerKey; onTagSelected(AutocompleteTag(-1, trigger.query.trim().lowercase(java.util.Locale.ROOT)), trigger.startIndex, cursor) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.usability_keep_text)) }, modifier = Modifier.testTag("inline_keep_text"), onClick = { dismissedTrigger = triggerKey })
            }
        }
    }
}

enum class TriggerType { CONTACT, TAG }
data class TriggerInfo(val type: TriggerType, val startIndex: Int, val query: String)
data class AutocompleteContact(val id: Long, val displayName: String, val phoneNumber: String? = null)
data class AutocompleteTag(val id: Long, val name: String)

/** People can contain spaces. Existing short names never terminate a new full-name query. */
@Suppress("UNUSED_PARAMETER")
fun detectTrigger(text: String, cursorPos: Int, contactNames: List<String> = emptyList()): TriggerInfo? {
    if (cursorPos <= 0 || cursorPos > text.length) return null
    val before = text.substring(0, cursorPos)
    val start = maxOf(before.lastIndexOf('@'), before.lastIndexOf('#'))
    if (start < 0 || (start > 0 && !text[start - 1].isWhitespace())) return null
    val query = text.substring(start + 1, cursorPos)
    if (query.any { it == '\n' || it == '\t' }) return null
    val type = if (text[start] == '@') TriggerType.CONTACT else TriggerType.TAG
    if (type == TriggerType.TAG && query.any { it.isWhitespace() }) return null
    return TriggerInfo(type, start, query)
}
