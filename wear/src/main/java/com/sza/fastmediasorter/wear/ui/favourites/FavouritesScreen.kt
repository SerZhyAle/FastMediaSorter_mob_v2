package com.sza.fastmediasorter.wear.ui.favourites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.browse.FileDeleteConfirmDialog
import com.sza.fastmediasorter.wear.ui.common.CellCaption
import com.sza.fastmediasorter.wear.ui.common.LongPressChip
import com.sza.fastmediasorter.wear.ui.common.ReceiverListDialog
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearFileActionsDialog
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.playerRouteFor
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.common.rememberWearRenameInput
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SINGLE_COLUMN = 1

/** The action menu acts on the pressed file alone; the confirmation reuses the selection wording. */
private const val SINGLE_FILE = 1
private val GRID_GAP = 4.dp
private val MESSAGE_HORIZONTAL_PADDING = 16.dp

/**
 * S1846: the Favourites section - what was marked on this watch, opened from here.
 *
 * Built on the same shape and the same cell as the two file lists that already exist, so a third list does
 * not become a third set of geometry decisions. A record written before this ticket carries no kind and no
 * thumbnail; it is still listed, because the user marked it, and it says why it will not open rather than
 * failing silently when tapped.
 */
@Composable
fun FavouritesScreen(
    navController: NavController,
    viewModel: FavouritesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewMode by viewModel.fileListViewMode.collectAsStateWithLifecycle()
    val listState = rememberWearListState()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()

    // Which menu is open is view state: a rotation that dropped it costs nothing, while a ViewModel
    // that carried it would replay it.
    var actionRecord by remember { mutableStateOf<WearFavoriteRecord?>(null) }
    var deleteRecord by remember { mutableStateOf<WearFavoriteRecord?>(null) }
    var sendToRecord by remember { mutableStateOf<WearFavoriteRecord?>(null) }
    val renameRecord = remember { mutableStateOf<WearFavoriteRecord?>(null) }
    val requestRename = rememberWearRenameInput { newName ->
        renameRecord.value?.let { viewModel.runOperation(it, WearFileOperation.Rename(newName)) }
    }

    // The tap is acted on once, in an effect keyed to the request, and the request is consumed straight
    // after - a recomposition must not push the player a second time.
    LaunchedEffect(openRequest) {
        val request = openRequest
        if (request is FavouriteOpenRequest.Ready) {
            navController.navigate(playerRouteFor(request.fileId, request.mimeType))
            viewModel.consumeOpenRequest()
        }
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        when (val current = state) {
            // Loading keeps its plain centred line: it is not one of the state block's three kinds,
            // and it offers nothing to act on because the answer is already on its way.
            is FavouritesUiState.Loading -> Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MESSAGE_HORIZONTAL_PADDING),
                textAlign = TextAlign.Center
            )

            // No retry: favourites are read from local storage, so there is no call to repeat.
            is FavouritesUiState.Empty -> WearStateBlock(
                kind = WearStateKind.EMPTY,
                message = stringResource(R.string.wear_favourites_empty),
                onBack = { navController.popBackStack() }
            )

            is FavouritesUiState.Content -> FavouritesList(
                unopenableNotice = openRequest is FavouriteOpenRequest.Unopenable,
                records = current.records,
                listState = listState,
                viewMode = viewMode,
                onOpen = viewModel::open,
                onUnmark = viewModel::unmark,
                onLongPress = { record -> actionRecord = record }
            )
        }
    }

    actionRecord?.let { record ->
        FavouriteActionsMenu(
            record = record,
            viewModel = viewModel,
            onClose = { actionRecord = null },
            onDelete = { deleteRecord = record },
            onRename = {
                renameRecord.value = record
                requestRename()
            },
            onSendTo = { sendToRecord = record }
        )
    }

    sendToRecord?.let { record ->
        ReceiverListDialog(
            receivers = remember(record.identity) { viewModel.sendToReceiversFor(record) },
            onPick = { entry ->
                sendToRecord = null
                viewModel.runOperation(record, WearFileOperation.SendToReceiver(entry.id))
            },
            onDismiss = { sendToRecord = null }
        )
    }

    deleteRecord?.let { record ->
        FileDeleteConfirmDialog(
            selectedCount = SINGLE_FILE,
            onConfirm = {
                deleteRecord = null
                viewModel.runOperation(record, WearFileOperation.Delete)
            },
            onDismiss = { deleteRecord = null }
        )
    }
}

/**
 * The long-press menu for one favourite row.
 *
 * Unmarking rides here rather than only in the row: the grid cell has no room for the second chip the
 * single-column layout carries, so in grid mode this menu is the only way to unmark.
 */
@Composable
private fun FavouriteActionsMenu(
    record: WearFavoriteRecord,
    viewModel: FavouritesViewModel,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onSendTo: () -> Unit
) {
    // Classifying a path canonicalises it, which touches the filesystem - asked once per pressed row
    // rather than on every recomposition the open dialog causes.
    val target = remember(record.identity) { viewModel.actionTargetFor(record) }
    val allowed = remember(record.identity) { viewModel.allowedOperationsFor(record) }
    WearFileActionsDialog(
        file = target,
        allowed = allowed,
        onPick = { kind ->
            onClose()
            when (kind) {
                WearFileOperationKind.DELETE -> onDelete()
                WearFileOperationKind.RENAME -> onRename()
                WearFileOperationKind.SEND_TO_PHONE ->
                    viewModel.runOperation(record, WearFileOperation.SendToPhone)
                WearFileOperationKind.MOVE_TO_PHONE ->
                    viewModel.runOperation(record, WearFileOperation.MoveToPhone)
                WearFileOperationKind.OPEN_ON_PHONE -> viewModel.reportOpenOnPhoneUnavailable()
                WearFileOperationKind.SEND_TO_RECEIVER -> onSendTo()
            }
        },
        onDismiss = onClose,
        onUnmark = {
            onClose()
            viewModel.unmark(record)
        }
    )
}

@Composable
private fun FavouritesList(
    unopenableNotice: Boolean,
    records: List<WearFavoriteRecord>,
    listState: ScalingLazyListState,
    viewMode: WearViewMode,
    onOpen: (WearFavoriteRecord) -> Unit,
    onUnmark: (WearFavoriteRecord) -> Unit,
    onLongPress: (WearFavoriteRecord) -> Unit
) {
    // The column count comes from the width this composable actually gets, exactly as both other file
    // lists decide it - the geometry question has one answer in this app, not three.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_section_favourites),
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }

            if (unopenableNotice) {
                item {
                    Text(
                        text = stringResource(R.string.wear_favourites_unopenable),
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            recordItems(
                records = records,
                columns = columns,
                onOpen = onOpen,
                onUnmark = onUnmark,
                onLongPress = onLongPress
            )
        }
    }
}

/** One column keeps the chip; more than one draws the cell both other watch file lists use. */
private fun ScalingLazyListScope.recordItems(
    records: List<WearFavoriteRecord>,
    columns: Int,
    onOpen: (WearFavoriteRecord) -> Unit,
    onUnmark: (WearFavoriteRecord) -> Unit,
    onLongPress: (WearFavoriteRecord) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(records) { record ->
            FavouriteChip(
                record = record,
                onOpen = onOpen,
                onUnmark = onUnmark,
                onLongPress = onLongPress
            )
        }
    } else {
        items(records.chunked(columns)) { rowRecords ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
            ) {
                rowRecords.forEach { record ->
                    ThumbnailCell(
                        thumbnail = WearThumbnail.Unavailable,
                        caption = record.displayName,
                        onClick = { onOpen(record) },
                        modifier = Modifier.weight(1f),
                        onLongClick = { onLongPress(record) },
                        // One glyph per record type: a screen full of favourites of the same kind is
                        // read by name only, so the name takes the cell (S2177).
                        captionLayout = CellCaption(overGroupIcon = true)
                    ) { glyphModifier ->
                        Icon(
                            imageVector = record.icon(),
                            contentDescription = null,
                            modifier = glyphModifier,
                            tint = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                }
                repeat(columns - rowRecords.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FavouriteChip(
    record: WearFavoriteRecord,
    onOpen: (WearFavoriteRecord) -> Unit,
    onUnmark: (WearFavoriteRecord) -> Unit,
    onLongPress: (WearFavoriteRecord) -> Unit
) {
    LongPressChip(
        onClick = { onOpen(record) },
        onLongClick = { onLongPress(record) },
        label = { Text(text = record.displayName) },
        secondaryLabel = if (record.mimeType == null) {
            { Text(text = stringResource(R.string.wear_favourites_unopenable)) }
        } else {
            null
        },
        icon = {
            Icon(
                imageVector = record.icon(),
                contentDescription = null
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
    Chip(
        onClick = { onUnmark(record) },
        label = { Text(text = stringResource(R.string.wear_favourites_unmark)) },
        modifier = Modifier.fillMaxWidth()
    )
    // The chip stays where it was: the single-column layout already reached unmarking in one tap,
    // and moving it into the menu would have cost that layout a tap to fix the grid's problem.
}

/** The kind picks the glyph; a record from before this ticket has none and gets the neutral file icon. */
private fun WearFavoriteRecord.icon(): ImageVector = when {
    mimeType == null -> Icons.AutoMirrored.Filled.InsertDriveFile
    mimeType.startsWith("image") -> Icons.Filled.Image
    mimeType.startsWith("video") -> Icons.Filled.Movie
    else -> Icons.Filled.Audiotrack
}
