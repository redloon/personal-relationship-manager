package com.idontremember.personalcrm.ui.recall

import com.idontremember.personalcrm.data.search.RecallKind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.ui.components.*


@Composable
fun RecallTypeFilters(selected: RecallKind?, onSelect: (RecallKind?) -> Unit, includeEntities: Boolean, prefix: String) {
    val kinds = if (includeEntities) listOf(null, RecallKind.PERSON, RecallKind.EVENT, RecallKind.FACT, RecallKind.FOLLOW_UP, RecallKind.TAG) else listOf(null, RecallKind.EVENT, RecallKind.FACT, RecallKind.FOLLOW_UP)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.forEach { kind ->
            val label = stringResource(when (kind) {
                RecallKind.PERSON -> R.string.recall_people
                RecallKind.EVENT -> R.string.search_notes_type
                RecallKind.FACT -> R.string.search_facts_type
                RecallKind.FOLLOW_UP -> R.string.search_followups_type
                RecallKind.TAG -> R.string.pilot_tags
                else -> R.string.search_all_types
            })
            FilterChip(selected = selected == kind, onClick = { onSelect(kind) }, label = { Text(label) }, modifier = Modifier.testTag("${prefix}_${kind ?: "all"}"))
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
internal fun BackIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) }
}
