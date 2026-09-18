package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.StandardWearAlertDialog
import com.sza.fastmediasorter.wear.ui.common.StandardWearChip
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateExtraAction
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.network.viewmodel.ConnectionTestState
import com.sza.fastmediasorter.wear.ui.network.viewmodel.ExportState
import com.sza.fastmediasorter.wear.ui.network.viewmodel.NetworkSourcesUiState
import com.sza.fastmediasorter.wear.ui.network.viewmodel.NetworkSourcesViewModel
import com.sza.fastmediasorter.wear.ui.network.viewmodel.SyncState
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private val PROGRESS_INDICATOR_SIZE = 24.dp
private val PROGRESS_INDICATOR_STROKE = 2.dp

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
    val listState = rememberWearListState(positionKey = WearRoutes.NETWORK_SOURCES)
    val stateScrollState = rememberScrollState()
    val viewMode by viewModel.viewMode.collectAsState()
    val connectionTestState by viewModel.connectionTestState.collectAsState()

    // State for delete confirmation dialog
    var pendingDeleteSource by remember { mutableStateOf<SourceItem?>(null) }

    // S1833: long press opens a choice of actions instead of heading straight for deletion.
    var pendingActionSource by remember { mutableStateOf<SourceItem?>(null) }

    // Navigate to sync_transfer immediately when sync request is pending
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Pending) {
            navController.navigate(WearRoutes.SYNC_TRANSFER)
        }
    }

    Timber.d("NetworkSourcesScreen composing with state: $uiState, sync: $syncState")

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        // The sources list is the only branch whose length is unbounded, but the state block that
        // replaces it scrolls too - and S2754 is the Play rejection for leaving that one unmarked.
        positionIndicator = {
            if (uiState is NetworkSourcesUiState.Success) {
                PositionIndicator(listState)
            } else {
                PositionIndicator(stateScrollState)
            }
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
                    offersCredentialEntry = viewModel.offersCredentialEntry,
                    actions = NetworkSourcesActions(
                        onSourceClick = { sourceId, sourceName ->
                            Timber.d("Source selected: $sourceName (ID: $sourceId)")
                            // S1781: the home screen's Last used section is fed from here - this is
                            // the only place a named resource is opened.
                            viewModel.rememberLastUsedResource(sourceId, sourceName)
                            // S1829: the media type is chosen on the next screen. This call used to
                            // pass a hard-coded "music", which was the only reason images and video on
                            // a network source were unreachable from the watch.
                            navController.navigate(
                                WearRoutes.sourceMediaType(sourceId, sourceName)
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
                        onSourceLongPress = { source ->
                            pendingActionSource = source
                        }
                    ),
                    exportState = exportState
                )
            }
            is NetworkSourcesUiState.Empty -> {
                // A failed sync is reported as the state's own message rather than stacked under the
                // hint: the two never describe the same situation, and the hint explains an emptiness
                // the failure has already superseded.
                val syncFailure = (syncState as? SyncState.Error)?.let { stringResource(it.messageRes) }
                WearStateBlock(
                    kind = if (syncFailure != null) WearStateKind.ERROR else WearStateKind.EMPTY,
                    message = syncFailure ?: stringResource(R.string.wear_resources_empty_hint),
                    onBack = { navController.popBackStack() },
                    scrollState = stateScrollState,
                    extraActions = emptyResourceActions(
                        syncState = syncState,
                        offersCredentialEntry = viewModel.offersCredentialEntry,
                        onSyncClick = { viewModel.requestSyncFromPhone() },
                        onAddClick = { navController.navigate(WearRoutes.ADD_NETWORK_SOURCE) }
                    )
                )
            }
            is NetworkSourcesUiState.Error -> {
                WearStateBlock(
                    kind = WearStateKind.ERROR,
                    message = state.message,
                    onBack = { navController.popBackStack() },
                    onRetry = {
                        Timber.d("Retrying network sources load")
                        viewModel.retryLoad()
                    },
                    scrollState = stateScrollState
                )
            }
        }
    }

    // Delete confirmation dialog
    pendingDeleteSource?.let { source ->
        StandardWearAlertDialog(
            show = true,
            title = stringResource(R.string.delete_source_confirm, source.name),
            onConfirm = {
                viewModel.deleteSource(source.id)
                pendingDeleteSource = null
            },
            onDismissRequest = { pendingDeleteSource = null },
            confirmLabel = stringResource(R.string.delete),
            cancelLabel = stringResource(R.string.cancel),
            isDestructive = true
        )
    }

    pendingActionSource?.let { source ->
        SourceActionsDialog(
            source = source,
            onTest = {
                viewModel.testSource(source.id, source.name)
                pendingActionSource = null
            },
            onDelete = {
                pendingDeleteSource = source
                pendingActionSource = null
            },
            onDismiss = { pendingActionSource = null }
        )
    }

    ConnectionTestDialog(
        state = connectionTestState,
        onDismiss = { viewModel.resetConnectionTestState() }
    )
}

/**
 * S1833: what a long press means now. The gesture used to lead straight to the delete confirmation,
 * which left a saved source with no way to be checked - the one thing wanted when it stops answering.
 * Deletion keeps its own confirmation, so the extra step costs nothing in safety.
 */
@Composable
private fun SourceActionsDialog(
    source: SourceItem,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    // Delete is the alert's own action and therefore the last row, which is where the module keeps a
    // destructive choice; testing the source sits above it as an ordinary content row.
    StandardWearAlertDialog(
        show = true,
        title = source.name,
        onConfirm = onDelete,
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.delete),
        cancelLabel = null,
        isDestructive = true,
        content = {
            StandardWearChip(
                label = stringResource(R.string.test_connection),
                onClick = onTest
            )
        }
    )
}

/** S1833: the outcome of checking a saved source, in the same words the add form uses. */
@Composable
private fun ConnectionTestDialog(
    state: ConnectionTestState,
    onDismiss: () -> Unit
) {
    when (state) {
        is ConnectionTestState.Testing -> {
            // The check runs on the phone's timeout, so the alert keeps a way out: without one a
            // source that never answers holds the screen until the timeout expires.
            StandardWearAlertDialog(
                show = true,
                title = state.sourceName,
                onConfirm = onDismiss,
                onDismissRequest = onDismiss,
                message = stringResource(R.string.testing_connection),
                confirmLabel = stringResource(android.R.string.ok),
                cancelLabel = null,
                content = {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(PROGRESS_INDICATOR_SIZE),
                        strokeWidth = PROGRESS_INDICATOR_STROKE
                    )
                }
            )
        }
        is ConnectionTestState.Finished -> {
            StandardWearAlertDialog(
                show = true,
                title = state.sourceName,
                onConfirm = onDismiss,
                onDismissRequest = onDismiss,
                confirmLabel = stringResource(android.R.string.ok),
                cancelLabel = null,
                content = {
                    Text(
                        text = state.message,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body2,
                        color = if (state.isError) {
                            MaterialTheme.colors.error
                        } else {
                            MaterialTheme.colors.onBackground
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }
        ConnectionTestState.Idle -> Unit
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
    val onSourceLongPress: (SourceItem) -> Unit = {}
)

@Composable
private fun SourcesListContent(
    sources: List<SourceItem>,
    syncState: SyncState,
    listState: ScalingLazyListState,
    viewMode: WearViewMode,
    offersCredentialEntry: Boolean,
    actions: NetworkSourcesActions,
    exportState: ExportState = ExportState.Idle
) {
    // The column count comes from the width this composable actually gets, never from the mode name -
    // the same rule the home screen applies, so the two screens cannot drift apart (strategic ADR-1).
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
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

            if (NetworkSourceEntry.isOffered(offersCredentialEntry)) {
                item {
                    Chip(
                        onClick = actions.onAddClick,
                        label = {
                            Text(
                                text = stringResource(R.string.add_network_source),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }

            if (syncState is SyncState.Error) {
                item {
                    Text(
                        text = stringResource(syncState.messageRes),
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

/**
 * The two ways out of an empty Resources list, in the order they are worth trying.
 *
 * Pulling the phone's sources over is the answer for almost everyone and stays first. Typing a
 * source in by hand is offered only where [NetworkSourceEntry.isOffered] allows it, which since S2486
 * is the `noLegal` flavor in both build types - so the store build shows one offer and Back, and the
 * extra chip is a sideload-only shape rather than the one Play reviews.
 */
@Composable
private fun emptyResourceActions(
    syncState: SyncState,
    offersCredentialEntry: Boolean,
    onSyncClick: () -> Unit,
    onAddClick: () -> Unit
): List<WearStateExtraAction> = buildList {
    add(
        WearStateExtraAction(
            label = stringResource(R.string.wear_sync_from_phone),
            onClick = onSyncClick,
            // A second tap while the phone is answering would queue a duplicate sync, which is the
            // one thing the pending state exists to prevent.
            enabled = syncState !is SyncState.Pending
        )
    )
    if (NetworkSourceEntry.isOffered(offersCredentialEntry)) {
        add(
            WearStateExtraAction(
                label = stringResource(R.string.add_network_source),
                onClick = onAddClick
            )
        )
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
                Text(
                    text = stringResource(R.string.wear_sync_from_phone),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
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
                Text(
                    text = stringResource(R.string.wear_export_to_phone),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        // S2522: spelled out through the base factory rather than through secondaryChipColors(), which
        // exposes no disabled colour and derives it as the content colour at a fixed low alpha - the
        // pattern AndroidX documents as insufficient contrast on a light background and patched only in
        // the primary family (b/254025377). This chip is disabled for the whole export, which is
        // exactly when it has to stay readable. The surface background matches the secondary look.
        colors = ChipDefaults.chipColors(
            backgroundColor = MaterialTheme.colors.surface,
            disabledContentColor = MaterialTheme.colors.onSurfaceVariant
        )
    )
}

data class SourceItem(
    val id: String,
    val name: String,
    val server: String,
    // S1952: the list showed one glyph for every row because the type never reached the UI model,
    // so a share and a cloud account were indistinguishable at a glance.
    val type: NetworkSourceType,
    // S2129: the type alone still leaves several shares of one kind identical. Null means the phone
    // never sent an id - the row falls back to the type glyph rather than losing its icon.
    val iconId: String? = null
)
