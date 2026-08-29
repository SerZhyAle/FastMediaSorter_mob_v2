package com.sza.fastmediasorter.wear.ui.streams

import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import androidx.wear.input.RemoteInputIntentHelper
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.CellCaption
import com.sza.fastmediasorter.wear.ui.common.RectangularButton
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearChoiceGridFit
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateExtraAction
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.wearChoiceRows
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionScroll
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private const val SINGLE_COLUMN = 1
private const val KEY_SEARCH_QUERY = "search_query"

// S1962: the toolbar buttons were 40 dp, under the project's own interactive minimum. The constant is
// the same one GridColumnFit drops a column to protect, so the toolbar and the grid now answer the
// touch-target question with one number instead of two. VideoActionButtons already sits at 48 dp.
// S1945: measured 2026-08-26 on Wear_OS_XL_Round (480x480 px / 240x240 dp, round) - the pinned row's
// three buttons render at bounds x 72-408 px, y 56-152 px (centre ~52 dp from the top). `adb.ps1
// clip-check` reports none of the three off-glass against the device's own rounded-corner mask; the
// only clipped nodes on that screen are channel cards scrolled past the edge, unrelated to this row.
// No extra round-screen inset is needed beyond the shared `wearScreenInsets()` already applied below.
private val TOOLBAR_BUTTON_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val TOOLBAR_ROW_PADDING = 4.dp

// S2178: what the pinned row actually occupies, as opposed to the button inside it. Anything pushed
// below the row must clear this, not TOOLBAR_BUTTON_SIZE - the 8 dp difference is the row's own
// vertical padding, and using the button size left exactly that much content under the icons.
private val TOOLBAR_ROW_HEIGHT = TOOLBAR_BUTTON_SIZE + TOOLBAR_ROW_PADDING * 2
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CELL_ICON_SIZE = 24.dp

private data class StreamsActions(
    val onRefresh: () -> Unit,
    val onChannelClick: (WearStreamChannel) -> Unit,
    val onSearchClick: () -> Unit,
    val onFilterClick: () -> Unit,
    val onSortClick: () -> Unit,
    val onClearSearch: () -> Unit,
    val onBack: () -> Unit
)

private data class StreamsControlState(
    val searchQuery: String,
    val filterKind: StreamFilterKind,
    val sortOrder: StreamSortOrder,
    val selectedTopic: String?,
    val selectedLanguage: String?
)

private data class StreamsFilterDialogState(
    val selectedFilter: StreamFilterKind,
    val selectedTopic: String?,
    val selectedLanguage: String?,
    val availableTopics: List<String>,
    val availableLanguages: List<String>
)

private data class StreamsFilterDialogActions(
    val onFilterSelected: (StreamFilterKind) -> Unit,
    val onTopicSelected: (String?) -> Unit,
    val onLanguageSelected: (String?) -> Unit
)

@Composable
fun StreamsScreen(
    navController: NavController,
    viewModel: StreamsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // S1945: the default `initialCenterItemIndex = 1` centers the list's 2nd item at open and leaves
    // the 1st before it - exactly where the now-pinned toolbar paints. Centering item 0 instead keeps
    // the first channel out from under the toolbar without touching the (unaffected) scroll behaviour.
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    // S1954: the player is the other place a channel can be marked, and coming back from it does not
    // re-emit the catalogue - so the pinned order is re-read here rather than only on a catalogue change.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPinnedStreams()
    }

    val searchHint = stringResource(R.string.wear_streams_search_hint)
    val searchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { resultIntent ->
            val remoteResults = RemoteInput.getResultsFromIntent(resultIntent)
            // S1946: the keyed answer first, then any other text the bundle carries. A watch that
            // returns the typed string under a key of its own choosing used to be read as "the user
            // entered nothing", which is the same screen as a search that matched everything.
            val remoteQuery = remoteResults?.let { results ->
                results.getCharSequence(KEY_SEARCH_QUERY)?.toString()
                    ?: results.keySet().firstNotNullOfOrNull { key ->
                        results.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
                    }
            }
            val query = remoteQuery
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
        onClearSearch = { viewModel.setSearchQuery("") },
        onBack = { navController.popBackStack() }
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
    val remoteInput = RemoteInput.Builder(KEY_SEARCH_QUERY)
        .setLabel(searchHint)
        .build()
    val remoteInputIntent = RemoteInputIntentHelper.createActionRemoteInputIntent()
    RemoteInputIntentHelper.putRemoteInputsExtra(remoteInputIntent, listOf(remoteInput))
    try {
        launch(remoteInputIntent)
    } catch (_: ActivityNotFoundException) {
        Timber.w("Wear remote input is unavailable")
        onUnavailable()
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
            state = StreamsFilterDialogState(
                selectedFilter = uiState.filterKind,
                selectedTopic = uiState.selectedTopic,
                selectedLanguage = uiState.selectedLanguage,
                availableTopics = uiState.availableTopics,
                availableLanguages = uiState.availableLanguages
            ),
            actions = StreamsFilterDialogActions(
                onFilterSelected = viewModel::setFilterKind,
                onTopicSelected = viewModel::setSelectedTopic,
                onLanguageSelected = viewModel::setSelectedLanguage
            ),
            viewMode = uiState.viewMode,
            onDismiss = { viewModel.setShowFilterDialog(false) }
        )
    }
    if (uiState.showSortDialog) {
        StreamSortDialog(
            selectedSort = uiState.sortOrder,
            viewMode = uiState.viewMode,
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
        val screenInsets = wearScreenInsets()
        val stillArriving = uiState.isLoading && uiState.channels.isEmpty()

        // The empty and failed cases take the whole screen rather than a row inside the list, which
        // is what every other browse screen does and what the shared block is shaped for. The control
        // header below stays composed on top either way, so search, filter and sort remain reachable
        // when a narrowing query is what emptied the list.
        if (uiState.displayChannels.isEmpty() && !stillArriving) {
            StreamsStateBlock(uiState = uiState, actions = actions)
        } else {
            ScalingLazyColumn(
                // S2049: the only list-like screen in the module with no rotary hookup - the crown
                // already scrolls the player and steps the calculator, so its silence here read as a
                // real gap, not a deliberate one. Plain scroll, not a stepped action: nothing here
                // consumes discrete steps.
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryActionScroll(listState),
                state = listState,
                contentPadding = PaddingValues(
                    start = screenInsets.calculateLeftPadding(LayoutDirection.Ltr),
                    top = screenInsets.calculateTopPadding() + TOOLBAR_ROW_HEIGHT,
                    end = screenInsets.calculateRightPadding(LayoutDirection.Ltr),
                    bottom = screenInsets.calculateBottomPadding()
                ),
                // S1945: matching autoCentering's itemIndex to the state's initialCenterItemIndex
                // (both 0) measured no change at all - centering targets a scaled viewport position,
                // not a plain top offset, so it keeps fighting contentPadding.top regardless of which
                // item it targets. Disabling it outright is the library's own documented alternative
                // for a developer-picked position (ScalingLazyColumn.kt:237-239): with it off,
                // contentPadding is what places items.
                autoCentering = null,
                scalingParams = WearGridScalingParams
            ) {
                streamsSearchState(uiState = uiState, onClearSearch = actions.onClearSearch)

                if (stillArriving) {
                    streamsLoading()
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
                        RefreshFooterChip(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = actions.onRefresh
                        )
                    }
                }
            }
        }

        StreamsControlHeader(
            state = StreamsControlState(
                searchQuery = uiState.searchQuery,
                filterKind = uiState.filterKind,
                sortOrder = uiState.sortOrder,
                selectedTopic = uiState.selectedTopic,
                selectedLanguage = uiState.selectedLanguage
            ),
            onSearchClick = actions.onSearchClick,
            onFilterClick = actions.onFilterClick,
            onSortClick = actions.onSortClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(screenInsets)
        )
    }
}

/**
 * S1946: what the list says about the search itself - the refusal of the input path, and the query
 * that is currently narrowing the list. Its own scope function for the reason [streamsLoading] is
 * one: the screen body is at detekt's length ceiling.
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

/**
 * What the screen says when the list is empty, and why refresh is not always a Retry.
 *
 * A failed update is an error whose retry is exactly the call that failed, so it takes the Retry
 * slot. An empty catalogue is not a failure - the fetch succeeded and returned nothing - so per the
 * block's own rule it carries no Retry, and the refresh is offered as the screen's own action
 * instead. Both keep a visible way back.
 */
@Composable
private fun StreamsStateBlock(
    uiState: StreamsUiState,
    actions: StreamsActions
) {
    val failed = uiState.error != null
    val refreshLabel = stringResource(R.string.wear_streams_refresh)
    LaunchedEffect(failed) { Timber.d("S2178: streams state block offset below the pinned row") }
    WearStateBlock(
        // S2178: the control header keeps painting over this branch, so the block centres its message
        // in the area below the row rather than in the whole screen. Passed as the caller's modifier
        // because the block applies that one before its own fillMaxSize, which is what shrinks the
        // centring area; every other caller of the block has nothing pinned above it.
        modifier = Modifier.padding(top = TOOLBAR_ROW_HEIGHT),
        kind = if (failed) WearStateKind.ERROR else WearStateKind.EMPTY,
        message = if (failed) {
            stringResource(R.string.wear_streams_update_failed)
        } else {
            stringResource(R.string.wear_streams_empty)
        },
        onBack = actions.onBack,
        onRetry = if (failed) actions.onRefresh else null,
        extraActions = if (failed) {
            emptyList()
        } else {
            listOf(WearStateExtraAction(label = refreshLabel, onClick = actions.onRefresh))
        }
    )
}

/**
 * The spinner shown while the first list is still arriving.
 *
 * Stays a list item, unlike the empty and failed cases: loading is not one of the state block's
 * three kinds, and it offers nothing to act on because the answer is already on its way.
 */
private fun ScalingLazyListScope.streamsLoading() {
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
    state: StreamsControlState,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TOOLBAR_ROW_PADDING),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RectangularButton(
            onClick = {
                onSearchClick()
            },
            modifier = Modifier.size(TOOLBAR_BUTTON_SIZE),
            colors = if (state.searchQuery.isNotEmpty()) {
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
            colors = if (
                state.filterKind != StreamFilterKind.ALL ||
                !state.selectedTopic.isNullOrBlank() ||
                !state.selectedLanguage.isNullOrBlank()
            ) {
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
            colors = if (state.sortOrder != StreamSortOrder.DEFAULT) {
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
    state: StreamsFilterDialogState,
    actions: StreamsFilterDialogActions,
    viewMode: WearViewMode,
    onDismiss: () -> Unit
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberScalingLazyListState()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val gridFit = WearChoiceGridFit(
                viewMode = viewMode,
                availableWidthDp = maxWidth.value.toInt(),
                fixedEnumeration = true
            )
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

                wearChoiceRows(
                    options = listOf(StreamFilterKind.ALL, StreamFilterKind.AUDIO_ONLY, StreamFilterKind.VIDEO_ONLY),
                    selected = state.selectedFilter,
                    labelOf = { filter ->
                        when (filter) {
                            StreamFilterKind.ALL -> stringResource(R.string.wear_streams_filter_all)
                            StreamFilterKind.AUDIO_ONLY -> stringResource(R.string.wear_streams_filter_audio)
                            StreamFilterKind.VIDEO_ONLY -> stringResource(R.string.wear_streams_filter_video)
                        }
                    },
                    onSelected = { actions.onFilterSelected(it) },
                    gridFit = gridFit
                )

                streamTopicFilterChoices(state, actions, gridFit)
                streamLanguageFilterChoices(state, actions, gridFit)
            }
        }
    }
}

private fun ScalingLazyListScope.streamTopicFilterChoices(
    state: StreamsFilterDialogState,
    actions: StreamsFilterDialogActions,
    gridFit: WearChoiceGridFit
) {
    if (state.availableTopics.isEmpty()) return
    item {
        Text(
            text = stringResource(R.string.wear_streams_filter_topic_header),
            style = MaterialTheme.typography.caption1,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            textAlign = TextAlign.Center
        )
    }
    wearChoiceRows(
        options = listOf<String?>(null) + state.availableTopics,
        selected = state.selectedTopic,
        labelOf = { topic -> topic ?: stringResource(R.string.wear_streams_filter_topic_all) },
        onSelected = { actions.onTopicSelected(it) },
        gridFit = gridFit.copy(fixedEnumeration = false)
    )
}

private fun ScalingLazyListScope.streamLanguageFilterChoices(
    state: StreamsFilterDialogState,
    actions: StreamsFilterDialogActions,
    gridFit: WearChoiceGridFit
) {
    if (state.availableLanguages.isEmpty()) return
    item {
        Text(
            text = stringResource(R.string.wear_streams_filter_language_header),
            style = MaterialTheme.typography.caption1,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            textAlign = TextAlign.Center
        )
    }
    wearChoiceRows(
        options = listOf<String?>(null) + state.availableLanguages,
        selected = state.selectedLanguage,
        labelOf = { language -> language ?: stringResource(R.string.wear_streams_filter_language_all) },
        onSelected = { actions.onLanguageSelected(it) },
        gridFit = gridFit.copy(fixedEnumeration = false)
    )
}

@Composable
private fun StreamSortDialog(
    selectedSort: StreamSortOrder,
    viewMode: WearViewMode,
    onSortSelected: (StreamSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberScalingLazyListState()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val gridFit = WearChoiceGridFit(
                viewMode = viewMode,
                availableWidthDp = maxWidth.value.toInt(),
                fixedEnumeration = true
            )
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

                wearChoiceRows(
                    options = StreamSortOrder.entries,
                    selected = selectedSort,
                    labelOf = { sort ->
                        val res = when (sort) {
                            StreamSortOrder.DEFAULT -> R.string.wear_streams_sort_default
                            StreamSortOrder.NAME_ASC -> R.string.wear_streams_sort_name_asc
                            StreamSortOrder.NAME_DESC -> R.string.wear_streams_sort_name_desc
                            StreamSortOrder.KIND -> R.string.wear_streams_sort_kind
                            StreamSortOrder.TOPIC -> R.string.wear_streams_sort_topic
                            StreamSortOrder.LANGUAGE -> R.string.wear_streams_sort_language
                        }
                        stringResource(res)
                    },
                    onSelected = onSortSelected,
                    gridFit = gridFit
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
        modifier = modifier,
        // Every channel whose favicon has not resolved draws the same ic_cast, so the name is the
        // only thing telling one tile from the next - the grid the 2026-08-27 audit found reading
        // six times as "S1945 Seed.." (S2177).
        captionLayout = CellCaption(overGroupIcon = true)
    ) { glyphModifier ->
        Icon(
            painter = painterResource(R.drawable.ic_cast),
            contentDescription = null,
            modifier = glyphModifier
        )
    }
}
