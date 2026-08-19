package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.network.viewmodel.ExportState
import com.sza.fastmediasorter.wear.ui.network.viewmodel.NetworkSourcesUiState
import com.sza.fastmediasorter.wear.ui.network.viewmodel.NetworkSourcesViewModel
import com.sza.fastmediasorter.wear.ui.network.viewmodel.SyncState
import com.sza.fastmediasorter.wear.util.GridColumnFit
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
    val exportState by viewModel.exportState.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()

    // State for delete confirmation dialog
    var pendingDeleteSource by remember { mutableStateOf<SourceItem?>(null) }

    // Navigate to sync_transfer immediately when sync request is pending
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Pending) {
            navController.navigate(WearRoutes.SYNC_TRANSFER)
        }
    }

    Timber.d("NetworkSourcesScreen composing with state: $uiState, sync: $syncState")

    val listState = rememberScalingLazyListState()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        // The sources list is the only branch whose length is unbounded, so it is the only one whose
        // position is worth indicating.
        positionIndicator = if (uiState is NetworkSourcesUiState.Success) {
            { PositionIndicator(listState) }
        } else {
            null
        }
    ) {
        when (val state = uiState) {
            is NetworkSourcesUiState.Loading -> {
                LoadingContent()
            }
            is NetworkSourcesUiState.Success -> {
                SourcesListContent(
                    sources = state.sources,
                    syncState = syncState,
                    listState = listState,
                    viewMode = viewMode,
                    actions = NetworkSourcesActions(
                        onSourceClick = { sourceId, sourceName ->
                            Timber.d("Source selected: $sourceName (ID: $sourceId)")
                            // S1781: the home screen's Last used section is fed from here - this is
                            // the only place a named resource is opened.
                            viewModel.rememberLastUsedResource(sourceName)
                            navController.navigate(
                                WearRoutes.browseSource(MUSIC_MEDIA_TYPE, sourceId, sourceName)
                            )
                        },
                        onAddClick = {
                            navController.navigate(WearRoutes.ADD_NETWORK_SOURCE)
                        },
                        onSyncClick = {
                            viewModel.requestSyncFromPhone()
                        },
                        onExportClick = {
                            viewModel.exportToPhone()
                        },
                        onDeleteClick = { source ->
                            pendingDeleteSource = source
                        }
                    ),
                    exportState = exportState
                )
            }
            is NetworkSourcesUiState.Empty -> {
                EmptyContent(
                    syncState = syncState,
                    onAddClick = {
                        navController.navigate(WearRoutes.ADD_NETWORK_SOURCE)
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
                        viewModel.retryLoad()
                    }
                )
            }
        }
    }

    // Delete confirmation dialog
    pendingDeleteSource?.let { source ->
        Alert(
            title = {
                Text(
                    text = stringResource(R.string.delete_source_confirm, source.name),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.title3
                )
            },
            negativeButton = {
                Chip(
                    onClick = { pendingDeleteSource = null },
                    label = { Text(stringResource(R.string.cancel)) },
                    colors = ChipDefaults.secondaryChipColors()
                )
            },
            positiveButton = {
                Chip(
                    onClick = {
                        viewModel.deleteSource(source.id)
                        pendingDeleteSource = null
                    },
                    label = { Text(stringResource(R.string.delete)) },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        )
    }
}

@Composable
private fun LoadingContent() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(wearScreenInsets()),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
    }
}

/** The list's five callbacks, grouped so the composable stays inside the parameter-count limit. */
internal data class NetworkSourcesActions(
    val onSourceClick: (String, String) -> Unit,
    val onAddClick: () -> Unit,
    val onSyncClick: () -> Unit,
    val onExportClick: () -> Unit = {},
    val onDeleteClick: (SourceItem) -> Unit = {}
)

@Composable
private fun SourcesListContent(
    sources: List<SourceItem>,
    syncState: SyncState,
    listState: ScalingLazyListState,
    viewMode: WearViewMode,
    actions: NetworkSourcesActions,
    exportState: ExportState = ExportState.Idle
) {
    // The column count comes from the width this composable actually gets, never from the mode name -
    // the same rule the home screen applies, so the two screens cannot drift apart (strategic ADR-1).
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets()
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

            sourceItems(sources = sources, columns = columns, actions = actions)

            item {
                SyncFromPhoneChip(syncState = syncState, onClick = actions.onSyncClick)
            }

            item {
                ExportToPhoneChip(exportState = exportState, onClick = actions.onExportClick)
            }

            if (exportState is ExportState.Success) {
                item {
                    Text(
                        text = stringResource(R.string.wear_export_success),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
            }

            if (NetworkSourceEntry.isOffered(BuildConfig.DEBUG)) {
                item {
                    Chip(
                        onClick = actions.onAddClick,
                        label = {
                            Text(text = stringResource(R.string.add_network_source))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
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
}

@Composable
private fun EmptyContent(
    syncState: SyncState,
    onAddClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = wearScreenInsets()
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
                text = stringResource(R.string.wear_resources_empty_hint),
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

        if (NetworkSourceEntry.isOffered(BuildConfig.DEBUG)) {
            item {
                Chip(
                    onClick = onAddClick,
                    label = {
                        Text(text = stringResource(R.string.add_network_source))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
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
private fun ExportToPhoneChip(
    exportState: ExportState,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        enabled = exportState !is ExportState.Exporting,
        label = {
            if (exportState is ExportState.Exporting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(text = stringResource(R.string.wear_export_to_phone))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = wearScreenInsets()
    ) {
        item {
            Text(
                text = stringResource(R.string.error),
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
                    Text(text = stringResource(R.string.retry))
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

/** A network source opens on its music tab; the other tabs are reached from there. */
private const val MUSIC_MEDIA_TYPE = "music"
