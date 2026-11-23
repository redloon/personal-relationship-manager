package com.idontremember.personalcrm.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.ui.components.SearchField
import com.idontremember.personalcrm.ui.components.exposeTestTags
import com.idontremember.personalcrm.ui.recall.BackIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onBack: () -> Unit,
    onTag: (Long) -> Unit,
    modifier: Modifier = Modifier,
    vm: TagManagementViewModel = hiltViewModel()
) {
    val tags by vm.tags.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    Scaffold(modifier = modifier.testTag("tags_screen"), topBar = {
        TopAppBar(title = {
            Text(stringResource(R.string.pilot_tags))
        }, navigationIcon = { BackIcon(onBack) })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                SearchField(
                    query,
                    {
                        query = it
                    },
                    stringResource(
                        R.string.search_tag_hint
                    ),
                    "clear_tags_search",
                    Modifier.fillMaxWidth().testTag("tags_search")
                )
            }
            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(name, {
                        name = it
                    }, label = {
                        Text(stringResource(R.string.pilot_new_tag))
                    }, modifier = Modifier.weight(1f), singleLine = true)
                    IconButton(onClick = {
                        vm.createTag(name)
                        name = ""
                    }, enabled = name.isNotBlank()) {
                        Icon(Icons.Default.Add, stringResource(R.string.pilot_add_tag))
                    }
                }
            }
            items(tags.filter { it.name.contains(query, true) }, key = { it.id }) { tag ->
                ListItem(
                    headlineContent = {
                        Text(tag.name)
                    },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                    trailingContent = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            IconButton(onClick = {
                                deleting = tag
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    stringResource(R.string.search_delete_tag, tag.name)
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                stringResource(R.string.search_open_tag, tag.name)
                            )
                        }
                    },
                    modifier = Modifier.testTag("tag_${tag.id}").clickable { onTag(tag.id) }
                )
            }
        }
    }
    deleting?.let { tag ->
        AlertDialog(
            modifier = Modifier.exposeTestTags(),
            onDismissRequest = { deleting = null },
            title = {
                Text(stringResource(R.string.pilot_delete_tag, tag.name))
            },
            text = { Text(stringResource(R.string.pilot_delete_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTag(tag)
                    deleting = null
                }) { Text(stringResource(R.string.pilot_delete)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleting = null
                }) { Text(stringResource(R.string.pilot_cancel)) }
            }
        )
    }
}
