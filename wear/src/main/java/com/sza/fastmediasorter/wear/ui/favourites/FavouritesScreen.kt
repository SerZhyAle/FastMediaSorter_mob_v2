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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SINGLE_COLUMN = 1
private val GRID_GAP = 4.dp

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
    val listState = rememberScalingLazyListState()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()

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
            is FavouritesUiState.Loading -> CentredMessage(text = stringResource(R.string.loading))

            is FavouritesUiState.Empty -> CentredMessage(
                text = stringResource(R.string.wear_favourites_empty)
            )

            is FavouritesUiState.Content -> FavouritesList(
                unopenableNotice = openRequest is FavouriteOpenRequest.Unopenable,
                records = current.records,
                listState = listState,
                viewMode = viewMode,
                onOpen = viewModel::open,
                onUnmark = viewModel::unmark
            )
        }
    }
}

/**
 * Which player renders a favourite the view model has already handed over.
 *
 * The kind decides, never the row's position - and a record with no kind never reaches here, because the
 * view model answers `Unopenable` for it. Guessing a player would be worse than refusing: a document sent
 * to the audio player fails further from its cause, which is the failure mode this whole ticket is about.
 */
private fun playerRouteFor(fileId: Long, mimeType: String): String = when {
    mimeType.startsWith("image") -> WearRoutes.imageViewer(fileId)
    mimeType.startsWith("video") -> WearRoutes.videoPlayer(fileId)
    else -> WearRoutes.audioPlayer(fileId)
}

@Composable
private fun CentredMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun FavouritesList(
    unopenableNotice: Boolean,
    records: List<WearFavoriteRecord>,
    listState: ScalingLazyListState,
    viewMode: WearViewMode,
    onOpen: (WearFavoriteRecord) -> Unit,
    onUnmark: (WearFavoriteRecord) -> Unit
) {
    // The column count comes from the width this composable actually gets, exactly as both other file
    // lists decide it - the geometry question has one answer in this app, not three.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets(),
            scalingParams = WearGridScalingParams
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

            recordItems(records = records, columns = columns, onOpen = onOpen, onUnmark = onUnmark)
        }
    }
}

/** One column keeps the chip; more than one draws the cell both other watch file lists use. */
private fun ScalingLazyListScope.recordItems(
    records: List<WearFavoriteRecord>,
    columns: Int,
    onOpen: (WearFavoriteRecord) -> Unit,
    onUnmark: (WearFavoriteRecord) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(records) { record ->
            FavouriteChip(record = record, onOpen = onOpen, onUnmark = onUnmark)
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = record.icon(),
                            contentDescription = null,
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
    onUnmark: (WearFavoriteRecord) -> Unit
) {
    Chip(
        onClick = { onOpen(record) },
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
}

/** The kind picks the glyph; a record from before this ticket has none and gets the neutral file icon. */
private fun WearFavoriteRecord.icon(): ImageVector = when {
    mimeType == null -> Icons.AutoMirrored.Filled.InsertDriveFile
    mimeType.startsWith("image") -> Icons.Filled.Image
    mimeType.startsWith("video") -> Icons.Filled.Movie
    else -> Icons.Filled.Audiotrack
}
