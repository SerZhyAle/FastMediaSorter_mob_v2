package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.network.viewmodel.NetworkSourcesUiState
import com.sza.fastmediasorter.wear.ui.network.viewmodel.NetworkSourcesViewModel
import com.sza.fastmediasorter.wear.ui.network.viewmodel.SyncState
import timber.log.Timber

/**
 * Screen for displaying available SMB network sources.
 * Shows list of saved connections and allows browsing files on selected source.
 */
@Composable
fun NetworkSourcesScreen(
    navController: NavController,
    viewModel: NetworkSourcesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    // Navigate to sync_transfer immediately when sync request is pending
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Pending) {
            navController.navigate("sync_transfer")
        }
    }

    Timber.d("NetworkSourcesScreen composing with state: $uiState, sync: $syncState")

    when (val state = uiState) {
        is NetworkSourcesUiState.Loading -> {
            LoadingContent()
        }
        is NetworkSourcesUiState.Success -> {
            SourcesListContent(
                sources = state.sources,
                syncState = syncState,
                onSourceClick = { sourceId, sourceName ->
                    Timber.d("Source selected: $sourceName (ID: $sourceId)")
                    navController.navigate("browse/music?sourceId=$sourceId&sourceName=$sourceName")
                },
                onAddClick = {
                    navController.navigate("add_smb")
                },
                onSyncClick = {
                    viewModel.requestSyncFromPhone()
                }
            )
        }
        is NetworkSourcesUiState.Empty -> {
            EmptyContent(
                syncState = syncState,
                onAddClick = {
                    navController.navigate("add_smb")
                },
                onSyncClick = {
                    viewModel.requestSyncFromPhone()
                }
            )
        }
        is NetworkSourcesUiState.Error -> {
            ErrorContent(
                message = state.message,
                onRetry = {
                    Timber.d("Retrying network sources load")
                    viewModel.loadSources()
                }
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = "⏳ Loading...",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SourcesListContent(
    sources: List<SourceItem>,
    syncState: SyncState,
    onSourceClick: (String, String) -> Unit,
    onAddClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.network_storage),
                style = MaterialTheme.typography.title2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        items(sources) { source ->
            Chip(
                onClick = { onSourceClick(source.id, source.name) },
                label = {
                    Text(text = "${source.name}\n${source.server}")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors()
            )
        }

        item {
            SyncFromPhoneChip(syncState = syncState, onClick = onSyncClick)
        }

        item {
            Chip(
                onClick = onAddClick,
                label = {
                    Text(text = stringResource(R.string.add_smb_connection))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }

        if (syncState is SyncState.Error) {
            item {
                Text(
                    text = syncState.message,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyContent(
    syncState: SyncState,
    onAddClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.network_storage),
                style = MaterialTheme.typography.title2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        item {
            Text(
                text = stringResource(R.string.wear_no_sources),
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        item {
            SyncFromPhoneChip(syncState = syncState, onClick = onSyncClick)
        }

        item {
            Chip(
                onClick = onAddClick,
                label = {
                    Text(text = stringResource(R.string.add_smb_connection))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }

        if (syncState is SyncState.Error) {
            item {
                Text(
                    text = syncState.message,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SyncFromPhoneChip(
    syncState: SyncState,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        enabled = syncState !is SyncState.Pending,
        label = {
            if (syncState is SyncState.Pending) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(text = stringResource(R.string.wear_sync_from_phone))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.primaryChipColors()
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Error",
                style = MaterialTheme.typography.title2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        
        item {
            Text(
                text = message,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )
        }
        
        item {
            Chip(
                onClick = onRetry,
                label = {
                    Text(text = "Retry")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

data class SourceItem(
    val id: String,
    val name: String,
    val server: String
)
