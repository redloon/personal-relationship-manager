package com.idontremember.personalcrm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/**
 * A text field that detects @ and # triggers to show autocomplete dropdowns.
 *
 * @param value Current text value
 * @param onValueChange Called when text changes
 * @param contacts Available contacts for @ autocomplete
 * @param tags Available tags for # autocomplete
 * @param onContactSelected Called when a contact is selected from @ dropdown
 * @param onTagSelected Called when a tag is selected from # dropdown
 * @param modifier Modifier
 * @param placeholder Placeholder text
 */
@Composable
fun AutocompleteTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    contacts: List<AutocompleteContact>,
    tags: List<AutocompleteTag>,
    onContactSelected: (AutocompleteContact) -> Unit,
    onTagSelected: (AutocompleteTag) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "What happened? Use @ to mention people, # for tags"
) {
    var showContactDropdown by remember { mutableStateOf(false) }
    var showTagDropdown by remember { mutableStateOf(false) }
    var triggerQuery by remember { mutableStateOf("") }
    var triggerStartIndex by remember { mutableStateOf(-1) }

    // Detect trigger characters
    val text = value.text
    val cursorPos = value.selection.start

    // Find if we're in a trigger context
    val triggerInfo = remember(text, cursorPos) {
        detectTrigger(text, cursorPos)
    }

    if (triggerInfo != null) {
        triggerStartIndex = triggerInfo.startIndex
        triggerQuery = triggerInfo.query

        when (triggerInfo.type) {
            TriggerType.CONTACT -> {
                val filtered = contacts.filter {
                    it.displayName.contains(triggerQuery, ignoreCase = true)
                }
                showContactDropdown = filtered.isNotEmpty()
                showTagDropdown = false

                if (showContactDropdown) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = {
                            showContactDropdown = false
                        },
                        properties = PopupProperties(focusable = false)
                    ) {
                        filtered.take(5).forEach { contact ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = contact.displayName,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (contact.phoneNumber != null) {
                                            Text(
                                                text = contact.phoneNumber,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showContactDropdown = false
                                    // Replace the @query with @DisplayName
                                    val before = text.substring(0, triggerStartIndex)
                                    val after = if (cursorPos < text.length) text.substring(cursorPos) else ""
                                    val newText = "$before@${contact.displayName} $after"
                                    val newCursor = before.length + contact.displayName.length + 2
                                    onValueChange(
                                        TextFieldValue(
                                            text = newText,
                                            selection = TextRange(newCursor)
                                        )
                                    )
                                    onContactSelected(contact)
                                }
                            )
                        }
                    }
                }
            }
            TriggerType.TAG -> {
                val filtered = tags.filter {
                    it.name.contains(triggerQuery, ignoreCase = true)
                }
                showTagDropdown = true // Always show, even if empty (to show "create new")
                showContactDropdown = false

                if (showTagDropdown) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = {
                            showTagDropdown = false
                        },
                        properties = PopupProperties(focusable = false)
                    ) {
                        filtered.take(5).forEach { tag ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "#${tag.name}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                onClick = {
                                    showTagDropdown = false
                                    val before = text.substring(0, triggerStartIndex)
                                    val after = if (cursorPos < text.length) text.substring(cursorPos) else ""
                                    val newText = "$before#${tag.name} $after"
                                    val newCursor = before.length + tag.name.length + 2
                                    onValueChange(
                                        TextFieldValue(
                                            text = newText,
                                            selection = TextRange(newCursor)
                                        )
                                    )
                                    onTagSelected(tag)
                                }
                            )
                        }
                        // "Create new" option if query doesn't match any existing tag
                        if (triggerQuery.isNotBlank() && filtered.none {
                                it.name.equals(triggerQuery.trim(), ignoreCase = true)
                            }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "+ Create \"${triggerQuery.trim()}\"",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showTagDropdown = false
                                    val tagName = triggerQuery.trim().lowercase()
                                    val before = text.substring(0, triggerStartIndex)
                                    val after = if (cursorPos < text.length) text.substring(cursorPos) else ""
                                    val newText = "$before#$tagName $after"
                                    val newCursor = before.length + tagName.length + 2
                                    onValueChange(
                                        TextFieldValue(
                                            text = newText,
                                            selection = TextRange(newCursor)
                                        )
                                    )
                                    onTagSelected(AutocompleteTag(id = -1, name = tagName))
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        showContactDropdown = false
        showTagDropdown = false
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag("note_text_field"),
        placeholder = { Text(placeholder) },
        minLines = 3,
        maxLines = 8
    )
}

enum class TriggerType {
    CONTACT, TAG
}

data class TriggerInfo(
    val type: TriggerType,
    val startIndex: Int,
    val query: String
)

data class AutocompleteContact(
    val id: Long,
    val displayName: String,
    val phoneNumber: String? = null
)

data class AutocompleteTag(
    val id: Long,
    val name: String
)

/**
 * Detect if the cursor is right after a @ or # trigger with a partial query.
 */
fun detectTrigger(text: String, cursorPos: Int): TriggerInfo? {
    if (cursorPos <= 0 || cursorPos > text.length) return null

    // Look backwards from cursor to find a trigger character
    val beforeCursor = text.substring(0, cursorPos)

    // Find the last @ or # that isn't preceded by a word character
    val atIdx = beforeCursor.lastIndexOf('@')
    val hashIdx = beforeCursor.lastIndexOf('#')

    // Use whichever trigger is more recent (closer to cursor)
    val triggerIdx = maxOf(atIdx, hashIdx)
    if (triggerIdx < 0) return null

    // Make sure the trigger is at the start of a word (preceded by space, newline, or start of text)
    if (triggerIdx > 0) {
        val charBefore = text[triggerIdx - 1]
        if (!charBefore.isWhitespace()) return null
    }

    // Check there's no space between trigger and cursor (the query shouldn't contain spaces)
    val queryStart = triggerIdx + 1
    val query = text.substring(queryStart, cursorPos)
    if (query.contains(' ') || query.contains('\n')) return null

    val type = if (text[triggerIdx] == '@') TriggerType.CONTACT else TriggerType.TAG
    return TriggerInfo(type = type, startIndex = triggerIdx, query = query)
}