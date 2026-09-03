package com.sza.fastmediasorter.wear.ui.tile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import kotlinx.coroutines.flow.collectLatest

/**
 * S1955: Screen for selecting which target (resource or stream) a tile represents.
 */
@Composable
fun TileTargetPickerScreen(
    navController: NavController,
    viewModel: TileTargetPickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberWearListState()

    LaunchedEffect(Unit) {
        viewModel.doneEvent.collectLatest {
            navController.popBackStack()
        }
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        PickerListContent(
            uiState = uiState,
            listState = listState,
            onSelectResource = viewModel::selectResource,
            onSelectStream = viewModel::selectStream
        )
    }
}

@Composable
private fun PickerListContent(
    uiState: TileTargetPickerUiState,
    listState: ScalingLazyListState,
    onSelectResource: (NetworkSource) -> Unit,
    onSelectStream: (WearStreamChannel) -> Unit
) {
    WearListColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        item {
            PickerHeader(kind = uiState.kind)
        }

        if (uiState.isLoading) {
            item {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        } else if (uiState.rows.isEmpty()) {
            item {
                PickerEmptyContent(kind = uiState.kind)
            }
        } else {
            items(uiState.rows) { row ->
                when (row) {
                    is PickerRow.ResourceRow -> {
                        Chip(
                            onClick = { onSelectResource(row.source) },
                            label = { Text(row.source.name) },
                            secondaryLabel = { Text(row.source.server) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                    is PickerRow.StreamRow -> {
                        Chip(
                            onClick = { onSelectStream(row.channel) },
                            label = { Text(row.channel.name) },
                            secondaryLabel = { Text(row.channel.url) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(kind: WearTileKind) {
    val titleRes = when (kind) {
        WearTileKind.RESOURCE -> R.string.wear_tile_picker_title_resource
        WearTileKind.STREAM -> R.string.wear_tile_picker_title_stream
        WearTileKind.FAVOURITES -> R.string.wear_tile_favourites_label
    }
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.title2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PickerEmptyContent(kind: WearTileKind) {
    val emptyRes = when (kind) {
        WearTileKind.RESOURCE -> R.string.wear_tile_picker_empty_resource
        WearTileKind.STREAM -> R.string.wear_tile_picker_empty_stream
        WearTileKind.FAVOURITES -> R.string.wear_tile_favourites_empty
    }
    Text(
        text = stringResource(emptyRes),
        style = MaterialTheme.typography.body2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center
    )
}
