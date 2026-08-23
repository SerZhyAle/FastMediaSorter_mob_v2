package com.sza.fastmediasorter.wear.ui.streams

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.ui.common.RectangularButton
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private const val SINGLE_COLUMN = 1
private const val KEY_SEARCH_QUERY = "search_query"

// S1962: the toolbar buttons were 40 dp, under the project's own interactive minimum. The constant is
// the same one GridColumnFit drops a column to protect, so the toolbar and the grid now answer the
// touch-target question with one number instead of two. VideoActionButtons already sits at 48 dp.
private val TOOLBAR_BUTTON_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CELL_ICON_SIZE = 24.dp
private val TITLE_VERTICAL_PADDING = 8.dp

private data class StreamsActions(
    val onRefresh: () -> Unit,
    val onChannelClick: (WearStreamChannel) -> Unit,
    val onSearchClick: () -> Unit,
    val onFilterClick: () -> Unit,
    val onSortClick: () -> Unit,
    val onClearSearch: () -> Unit
)

@Composable
fun StreamsScreen(
    navController: NavController,
    viewModel: StreamsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    val searchHint = stringResource(R.string.wear_streams_search_hint)
    val searchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val remoteResults = RemoteInput.getResultsFromIntent(result.data)
            // S1946: the keyed answer first, then any other text the bundle carries. A watch that
            // returns the typed string under a key of its own choosing used to be read as "the user
            // entered nothing", which is the same screen as a search that matched everything.
            val remoteQuery = remoteResults?.let { results ->
                results.getCharSequence(KEY_SEARCH_QUERY)?.toString()
                    ?: results.keySet().firstNotNullOfOrNull { key ->
                        results.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
                    }
            }
            val speechResults = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val query = remoteQuery ?: speechResults?.firstOrNull()
            if (!query.isNullOrBlank()) {
                viewModel.setSearchQuery(query)
            }
        }
    }

    val launchSearchInput: () -> Unit = {
        launchRemoteOrSpeechInput(
            searchHint = searchHint,
            onUnavailable = viewModel::setSearchInputUnavailable,
            launch = { searchLauncher.launch(it) },
        )
    }

    val actions = StreamsActions(
        onRefresh = { viewModel.refreshCatalog() },
        onChannelClick = { channel ->
            val target = viewModel.prepareStreamPlayback(channel)
            if (target.isVideo) {
                navController.navigate(WearRoutes.videoPlayer(target.fileId))
            } else {
                navController.navigate(WearRoutes.audioPlayer(target.fileId))
            }
        },
        onSearchClick = { viewModel.setShowSearchDialog(true) },
        onFilterClick = { viewModel.setShowFilterDialog(true) },
        onSortClick = { viewModel.setShowSortDialog(true) },
        onClearSearch = { viewModel.setSearchQuery("") }
    )

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        StreamsMainContent(
            uiState = uiState,
            listState = listState,
            getFaviconTile = viewModel::getFaviconTile,
            actions = actions
        )
    }

    StreamsDialogsHost(
        uiState = uiState,
        viewModel = viewModel,
        launchSearchInput = launchSearchInput
    )
}

private fun launchRemoteOrSpeechInput(
    searchHint: String,
    onUnavailable: () -> Unit,
    launch: (Intent) -> Unit,
) {
    val remoteInputIntent = Intent("androidx.wear.input.action.REMOTE_INPUT").apply {
        putExtra("androidx.wear.input.extra.DISALLOW_EMOJI", true)
        val remoteInput = RemoteInput.Builder(KEY_SEARCH_QUERY)
            .setLabel(searchHint)
            .build()
        putExtra("androidx.wear.input.extra.REMOTE_INPUTS", arrayOf(remoteInput))
    }
    try {
        launch(remoteInputIntent)
    } catch (_: ActivityNotFoundException) {
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, searchHint)
        }
        try {
            launch(speechIntent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Search input launcher failed")
            onUnavailable()
        }
    }
}

@Composable
private fun StreamsDialogsHost(
    uiState: StreamsUiState,
    viewModel: StreamsViewModel,
    launchSearchInput: () -> Unit
) {
    if (uiState.showSearchDialog) {
        StreamSearchDialog(
            searchQuery = uiState.searchQuery,
            onLaunchInput = launchSearchInput,
            onPresetSelected = viewModel::setSearchQuery,
            onClear = { viewModel.setSearchQuery("") },
            onDismiss = { viewModel.setShowSearchDialog(false) }
        )
    }
    if (uiState.showFilterDialog) {
        StreamFilterDialog(
            selectedFilter = uiState.filterKind,
            onFilterSelected = viewModel::setFilterKind,
            onDismiss = { viewModel.setShowFilterDialog(false) }
        )
    }
    if (uiState.showSortDialog) {
        StreamSortDialog(
            selectedSort = uiState.sortOrder,
            onSortSelected = viewModel::setSortOrder,
            onDismiss = { viewModel.setShowSortDialog(false) }
        )
    }
}

@Composable
private fun StreamsMainContent(
    uiState: StreamsUiState,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    getFaviconTile: suspend (Int?) -> Bitmap?,
    actions: StreamsActions
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(uiState.viewMode, maxWidth.value.toInt())
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets(),
            scalingParams = WearGridScalingParams
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

            item {
                StreamsControlHeader(
                    searchQuery = uiState.searchQuery,
                    filterKind = uiState.filterKind,
                    sortOrder = uiState.sortOrder,
                    onSearchClick = actions.onSearchClick,
                    onFilterClick = actions.onFilterClick,
                    onSortClick = actions.onSortClick
                )
            }

            streamsSearchState(uiState = uiState, onClearSearch = actions.onClearSearch)

            if (uiState.displayChannels.isEmpty()) {
                streamsEmptyOrLoading(uiState = uiState, onRefresh = actions.onRefresh)
            } else {
                streamItems(
                    channels = uiState.displayChannels,
                    columns = columns,
                    getFaviconTile = getFaviconTile,
                    onChannelClick = actions.onChannelClick
                )
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    RefreshFooterChip(isRefreshing = uiState.isRefreshing, onRefresh = actions.onRefresh)
                }
            }
        }
    }
}

/**
 * S1946: what the list says about the search itself - the refusal of the input path, and the query
 * that is currently narrowing the list. Its own scope function for the reason
 * [streamsEmptyOrLoading] is one: the screen body is at detekt's length ceiling.
 */
private fun ScalingLazyListScope.streamsSearchState(
    uiState: StreamsUiState,
    onClearSearch: () -> Unit
) {
    if (uiState.searchInputUnavailable) {
        item {
            Text(
                text = stringResource(R.string.wear_streams_search_unavailable),
                style = MaterialTheme.typography.caption2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    }

    if (uiState.searchQuery.isNotEmpty()) {
        item {
            Chip(
                onClick = onClearSearch,
                label = {
                    Text(
                        text = uiState.searchQuery,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.wear_streams_clear_search),
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

private fun ScalingLazyListScope.streamsEmptyOrLoading(
    uiState: StreamsUiState,
    onRefresh: () -> Unit
) {
    if (uiState.isLoading && uiState.channels.isEmpty()) {
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
    } else {
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
                    onClick = onRefresh,
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
}

@Composable
private fun RefreshFooterChip(
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    if (isRefreshing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    } else {
        Chip(
            onClick = onRefresh,
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

@Composable
private fun StreamsControlHeader(
    searchQuery: String,
    filterKind: StreamFilterKind,
    sortOrder: StreamSortOrder,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RectangularButton(
            onClick = {
                onSearchClick()
            },
            modifier = Modifier.size(TOOLBAR_BUTTON_SIZE),
            colors = if (searchQuery.isNotEmpty()) {
                ButtonDefaults.primaryButtonColors()
            } else {
                ButtonDefaults.secondaryButtonColors()
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.wear_streams_search),
                modifier = Modifier.size(20.dp)
            )
        }

        RectangularButton(
            onClick = onFilterClick,
            modifier = Modifier.size(TOOLBAR_BUTTON_SIZE),
            colors = if (filterKind != StreamFilterKind.ALL) {
                ButtonDefaults.primaryButtonColors()
            } else {
                ButtonDefaults.secondaryButtonColors()
            }
        ) {
            Icon(
                imageVector = Icons.Filled.FilterList,
                contentDescription = stringResource(R.string.wear_streams_filter),
                modifier = Modifier.size(20.dp)
            )
        }

        RectangularButton(
            onClick = onSortClick,
            modifier = Modifier.size(TOOLBAR_BUTTON_SIZE),
            colors = if (sortOrder != StreamSortOrder.DEFAULT) {
                ButtonDefaults.primaryButtonColors()
            } else {
                ButtonDefaults.secondaryButtonColors()
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.wear_streams_sort),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StreamSearchDialog(
    searchQuery: String,
    onLaunchInput: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets()
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_streams_search),
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            item {
                Chip(
                    onClick = {
                        onDismiss()
                        onLaunchInput()
                    },
                    label = { Text(stringResource(R.string.wear_streams_search_hint)) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Keyboard,
                            contentDescription = stringResource(R.string.wear_streams_search_hint),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )
            }

            if (searchQuery.isNotEmpty()) {
                item {
                    Chip(
                        onClick = onClear,
                        label = { Text(stringResource(R.string.wear_streams_clear_search)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.wear_streams_clear_search),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }

            val presets = listOf("Radio", "Music", "News", "TV")
            items(presets) { preset ->
                Chip(
                    onClick = { onPresetSelected(preset) },
                    label = { Text(preset) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

@Composable
private fun StreamFilterDialog(
    selectedFilter: StreamFilterKind,
    onFilterSelected: (StreamFilterKind) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets()
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_streams_filter),
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            item {
                Chip(
                    onClick = { onFilterSelected(StreamFilterKind.ALL) },
                    label = { Text(stringResource(R.string.wear_streams_filter_all)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (selectedFilter == StreamFilterKind.ALL) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }

            item {
                Chip(
                    onClick = { onFilterSelected(StreamFilterKind.AUDIO_ONLY) },
                    label = { Text(stringResource(R.string.wear_streams_filter_audio)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (selectedFilter == StreamFilterKind.AUDIO_ONLY) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }

            item {
                Chip(
                    onClick = { onFilterSelected(StreamFilterKind.VIDEO_ONLY) },
                    label = { Text(stringResource(R.string.wear_streams_filter_video)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (selectedFilter == StreamFilterKind.VIDEO_ONLY) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }
        }
    }
}

@Composable
private fun StreamSortDialog(
    selectedSort: StreamSortOrder,
    onSortSelected: (StreamSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets()
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_streams_sort),
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            item {
                Chip(
                    onClick = { onSortSelected(StreamSortOrder.DEFAULT) },
                    label = { Text(stringResource(R.string.wear_streams_sort_default)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (selectedSort == StreamSortOrder.DEFAULT) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }

            item {
                Chip(
                    onClick = { onSortSelected(StreamSortOrder.NAME_ASC) },
                    label = { Text(stringResource(R.string.wear_streams_sort_name_asc)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (selectedSort == StreamSortOrder.NAME_ASC) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }

            item {
                Chip(
                    onClick = { onSortSelected(StreamSortOrder.NAME_DESC) },
                    label = { Text(stringResource(R.string.wear_streams_sort_name_desc)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (selectedSort == StreamSortOrder.NAME_DESC) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }

            item {
                Chip(
                    onClick = { onSortSelected(StreamSortOrder.KIND) },
                    label = { Text(stringResource(R.string.wear_streams_sort_kind)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (selectedSort == StreamSortOrder.KIND) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
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

    val bmp = faviconBitmap
    val thumbnail = if (bmp != null) WearThumbnail.Ready(bmp) else WearThumbnail.Unavailable

    ThumbnailCell(
        thumbnail = thumbnail,
        caption = channel.name,
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_cast),
            contentDescription = null,
            modifier = Modifier.size(CELL_ICON_SIZE)
        )
    }
}
