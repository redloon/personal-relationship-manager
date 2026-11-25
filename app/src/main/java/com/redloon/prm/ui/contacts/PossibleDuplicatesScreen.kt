package com.redloon.prm.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redloon.prm.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PossibleDuplicatesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsListViewModel = hiltViewModel()
) {
    val candidates by viewModel.reconnectCandidates.collectAsStateWithLifecycle()
    val busy by viewModel.reconnectBusy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val success = stringResource(R.string.people_reconnected)
    val failure = stringResource(R.string.people_reconnect_failed)
    LaunchedEffect(viewModel, success, failure) {
        viewModel.feedback.collect { feedback ->
            snackbar.showSnackbar(if (feedback == PeopleFeedback.Reconnected) success else failure)
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("possible_duplicates_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        pluralStringResource(
                            R.plurals.people_possible_duplicates_title,
                            candidates.size,
                            candidates.size
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("possible_duplicates_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text(stringResource(R.string.people_duplicates_explanation)) }
            if (candidates.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.people_duplicates_complete),
                        modifier = Modifier.testTag("duplicates_complete")
                    )
                }
            }
            items(candidates, key = {
                "${it.pair.savedProfileId}_${it.pair.androidProfileId}"
            }) { candidate ->
                PossibleDuplicateCard(
                    candidate,
                    busy,
                    onReconnect = { viewModel.reconnect(candidate) },
                    onDifferent = { viewModel.dismissReconnect(candidate) }
                )
            }
        }
    }
}
