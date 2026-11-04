package com.idontremember.personalcrm.ui.pilot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.idontremember.personalcrm.ui.components.exposeTestTags
import com.idontremember.personalcrm.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.idontremember.personalcrm.ui.components.*
import com.idontremember.personalcrm.data.db.entity.Tag
import com.idontremember.personalcrm.data.settings.DateDisplay
import com.idontremember.personalcrm.data.search.RecallEntry
import com.idontremember.personalcrm.data.search.RecallSearch
import com.idontremember.personalcrm.ui.followups.FollowUpsSection
import java.text.DateFormat
import java.util.Date

@Composable
fun RecallNoteRow(row: RecallEntry, query: String, dateDisplay: DateDisplay = DateDisplay.ABSOLUTE, onClick: () -> Unit) {
    val kind = stringResource(when (row.kind) { "fact" -> R.string.recall_fact; "followup" -> R.string.recall_followup; else -> R.string.recall_event })
    ListItem(
        headlineContent = { Text(RecallSearch.excerpt(row.text, query), maxLines = 3, overflow = TextOverflow.Ellipsis) },
        overlineContent = { Text(listOf(kind, row.title).filter { it.isNotBlank() }.joinToString(" · ")) },
        supportingContent = { val date = formatEventDateText(row.date, dateDisplay); Text(if(row.kind == "fact") stringResource(R.string.pilot_recorded, date) else date) },
        modifier = Modifier.fillMaxWidth().testTag("recall_${row.kind}_${row.id}").clickable(onClick = onClick)
    )
}
