package com.sza.fastmediasorter.wear.ui.streams

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CELL_BUTTON_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val CELL_ICON_SIZE = 24.dp
private val TITLE_VERTICAL_PADDING = 16.dp

@Composable
fun StreamsScreen(
    navController: NavController,
    viewModel: StreamsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(uiState.viewMode, maxWidth.value.toInt())
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = wearScreenInsets()
            ) {
                item {
                    Text(
                        text = stringResource(R.string.wear_section_streams),
                        style = MaterialTheme.typography.title2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TITLE_VERTICAL_PADDING),
                        textAlign = TextAlign.Center
                    )
                }

                when {
                    uiState.isLoading && uiState.channels.isEmpty() -> {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.wear_streams_updating),
                                    style = MaterialTheme.typography.caption2,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    uiState.channels.isEmpty() -> {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (uiState.error != null) {
                                        stringResource(R.string.wear_streams_update_failed)
                                    } else {
                                        stringResource(R.string.wear_streams_empty)
                                    },
                                    style = MaterialTheme.typography.body2,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Chip(
                                    onClick = { viewModel.refreshCatalog() },
                                    label = { Text(stringResource(R.string.wear_streams_refresh)) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = stringResource(R.string.wear_streams_refresh),
                                            modifier = Modifier.size(CELL_ICON_SIZE)
                                        )
                                    },
                                    colors = ChipDefaults.primaryChipColors()
                                )
                            }
                        }
                    }

                    else -> {
                        streamItems(
                            channels = uiState.channels,
                            columns = columns,
                            getFaviconTile = viewModel::getFaviconTile,
                            onChannelClick = { channel ->
                                val target = viewModel.prepareStreamPlayback(channel)
                                if (target.isVideo) {
                                    navController.navigate(WearRoutes.videoPlayer(target.fileId))
                                } else {
                                    navController.navigate(WearRoutes.audioPlayer(target.fileId))
                                }
                            }
                        )

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        item {
                            if (uiState.isRefreshing) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            } else {
                                Chip(
                                    onClick = { viewModel.refreshCatalog() },
                                    label = { Text(stringResource(R.string.wear_streams_refresh)) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = stringResource(R.string.wear_streams_refresh),
                                            modifier = Modifier.size(CELL_ICON_SIZE)
                                        )
                                    },
                                    colors = ChipDefaults.secondaryChipColors()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ScalingLazyListScope.streamItems(
    channels: List<WearStreamChannel>,
    columns: Int,
    getFaviconTile: suspend (Int?) -> Bitmap?,
    onChannelClick: (WearStreamChannel) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(channels, key = { it.url }) { channel ->
            StreamChip(
                channel = channel,
                getFaviconTile = getFaviconTile,
                onClick = { onChannelClick(channel) }
            )
        }
    } else {
        items(channels.chunked(columns)) { rowChannels ->
            StreamRow(
                channels = rowChannels,
                columns = columns,
                getFaviconTile = getFaviconTile,
                onChannelClick = onChannelClick
            )
        }
    }
}

@Composable
private fun StreamChip(
    channel: WearStreamChannel,
    getFaviconTile: suspend (Int?) -> Bitmap?,
    onClick: () -> Unit
) {
    val faviconBitmap by produceState<Bitmap?>(initialValue = null, channel.faviconIndex) {
        value = getFaviconTile(channel.faviconIndex)
    }

    Chip(
        onClick = onClick,
        label = {
            Text(
                text = channel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        icon = {
            val bmp = faviconBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = channel.name,
                    modifier = Modifier.size(CELL_ICON_SIZE)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_cast),
                    contentDescription = channel.name,
                    modifier = Modifier.size(CELL_ICON_SIZE)
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = channel.name },
        colors = ChipDefaults.primaryChipColors()
    )
}

@Composable
private fun StreamRow(
    channels: List<WearStreamChannel>,
    columns: Int,
    getFaviconTile: suspend (Int?) -> Bitmap?,
    onChannelClick: (WearStreamChannel) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        channels.forEach { channel ->
            StreamCell(
                channel = channel,
                modifier = Modifier.weight(1f),
                getFaviconTile = getFaviconTile,
                onClick = { onChannelClick(channel) }
            )
        }
        repeat(columns - channels.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StreamCell(
    channel: WearStreamChannel,
    modifier: Modifier,
    getFaviconTile: suspend (Int?) -> Bitmap?,
    onClick: () -> Unit
) {
    val faviconBitmap by produceState<Bitmap?>(initialValue = null, channel.faviconIndex) {
        value = getFaviconTile(channel.faviconIndex)
    }

    Column(
        modifier = modifier.semantics { contentDescription = channel.name },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(CELL_BUTTON_SIZE),
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            val bmp = faviconBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = channel.name,
                    modifier = Modifier.size(CELL_ICON_SIZE)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_cast),
                    contentDescription = channel.name,
                    modifier = Modifier.size(CELL_ICON_SIZE)
                )
            }
        }
        Text(
            text = channel.name,
            style = MaterialTheme.typography.caption3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
