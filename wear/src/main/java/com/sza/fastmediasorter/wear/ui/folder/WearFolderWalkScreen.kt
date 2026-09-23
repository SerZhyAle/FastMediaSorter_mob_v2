package com.sza.fastmediasorter.wear.ui.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearFolderEntry
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.model.contentTypeForEntry
import com.sza.fastmediasorter.wear.ui.common.CellCaption
import com.sza.fastmediasorter.wear.ui.common.CenteredGridRow
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.common.SingleColumnTileCell
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val TITLE_PADDING_VERTICAL = 12.dp

/**
 * S2201: the walk over the watch's own storage, one level at a time.
 * S2490: respects fileListViewMode so grid/columns mode (2 or 3 columns)
 * renders grid cells consistently with all browse screens.
 *
 * @param onOpenFile receives the tapped row, whose uri is non-null - only a file row invokes it.
 *   S2694 widened this from the bare uri and mime type: a network row's name, size and timestamp
 *   were read off the protocol by the level that produced it, and the host has no cheaper way back
 *   to them than a second listing of the same directory.
 * @param onOpenContainer S3383: receives the id a FileDO container was published under, instead of
 *   [onOpenFile] - a container opened in a player plays its encrypted bytes.
 * @param onExit called when Back is pressed at the level the walk started on.
 */
@Composable
fun WearFolderWalkScreen(
    onOpenFile: (WearFolderEntry) -> Unit,
    onOpenContainer: (Long) -> Unit,
    onExit: () -> Unit,
    viewModel: WearFolderWalkViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fileListViewMode by viewModel.fileListViewMode.collectAsStateWithLifecycle()
    val openFile: (WearFolderEntry) -> Unit = { entry ->
        viewModel.containerIdFor(entry)?.let(onOpenContainer) ?: onOpenFile(entry)
    }

    // Re-runs each time the walk re-enters composition, which is the return from a recovered copy's viewer.
    LaunchedEffect(Unit) { viewModel.discardOpenedContainers() }

    BackHandler(enabled = true) {
        if (!viewModel.navigateUp()) {
            onExit()
        }
    }

    val listState = rememberWearListState()
    val stateScrollState = rememberScrollState()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        // S2754: an empty folder draws the state block instead of the list, and the block carries its
        // own scroll - so the indicator moves with it rather than with the list standing still behind.
        positionIndicator = {
            if (state is WearFolderWalkUiState.Empty) {
                PositionIndicator(stateScrollState)
            } else {
                PositionIndicator(listState)
            }
        }
    ) {
        when (val current = state) {
            is WearFolderWalkUiState.Loading -> CircularProgressIndicator()

            is WearFolderWalkUiState.Empty -> WearStateBlock(
                kind = WearStateKind.EMPTY,
                onBack = { if (current.canGoUp) viewModel.navigateUp() else onExit() },
                scrollState = stateScrollState
            )

            is WearFolderWalkUiState.Content -> FolderWalkList(
                content = current,
                listState = listState,
                viewMode = fileListViewMode,
                onOpenFolder = viewModel::openFolder,
                onOpenFile = openFile,
                onLoadMore = viewModel::loadMore
            )
        }
    }
}

@Composable
private fun FolderWalkList(
    content: WearFolderWalkUiState.Content,
    listState: ScalingLazyListState,
    viewMode: WearViewMode,
    onOpenFolder: (WearFolderEntry) -> Unit,
    onOpenFile: (WearFolderEntry) -> Unit,
    onLoadMore: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                LevelTitle(title = content.title)
            }

            if (columns == SINGLE_COLUMN) {
                items(content.entries) { entry ->
                    FolderWalkRow(
                        entry = entry,
                        onOpenFolder = onOpenFolder,
                        onOpenFile = onOpenFile
                    )
                }
            } else {
                items(content.entries.chunked(columns)) { rowEntries ->
                    FolderWalkGridRow(
                        entries = rowEntries,
                        columns = columns,
                        onOpenFolder = onOpenFolder,
                        onOpenFile = onOpenFile
                    )
                }
            }

            if (content.canLoadMore) {
                item {
                    Chip(
                        onClick = onLoadMore,
                        label = {
                            Text(
                                text = stringResource(R.string.wear_folder_load_more),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelTitle(title: ScreenTitle) {
    val text = when (title) {
        is ScreenTitle.Text -> title.value
        is ScreenTitle.Resource -> stringResource(title.id)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.title3,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TITLE_PADDING_VERTICAL),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun FolderWalkGridRow(
    entries: List<WearFolderEntry>,
    columns: Int,
    onOpenFolder: (WearFolderEntry) -> Unit,
    onOpenFile: (WearFolderEntry) -> Unit
) {
    CenteredGridRow(columns = columns, itemCount = entries.size, gap = GRID_GAP) {
        entries.forEach { entry ->
            val type = contentTypeForEntry(entry.mimeType, entry.isDirectory)
            ThumbnailCell(
                thumbnail = WearThumbnail.Unavailable,
                caption = entry.name,
                onClick = {
                    val uri = entry.uri
                    when {
                        entry.isDirectory -> onOpenFolder(entry)
                        uri != null -> onOpenFile(entry)
                        else -> Timber.w("Folder entry is neither a directory nor a file: %s", entry.name)
                    }
                },
                modifier = Modifier.weight(1f),
                captionLayout = CellCaption(overGroupIcon = true)
            ) { glyphModifier ->
                Icon(
                    painter = painterResource(ContentTypeCatalog.iconFor(type)),
                    contentDescription = null,
                    tint = if (ContentTypeCatalog.isMonochrome(type)) {
                        colorResource(ContentTypeCatalog.tintFor(type))
                    } else {
                        Color.Unspecified
                    },
                    modifier = glyphModifier
                )
            }
        }
    }
}

@Composable
private fun FolderWalkRow(
    entry: WearFolderEntry,
    onOpenFolder: (WearFolderEntry) -> Unit,
    onOpenFile: (WearFolderEntry) -> Unit
) {
    val type = contentTypeForEntry(entry.mimeType, entry.isDirectory)
    SingleColumnTileCell(
        thumbnail = WearThumbnail.Unavailable,
        caption = entry.name,
        onClick = {
            val uri = entry.uri
            when {
                entry.isDirectory -> onOpenFolder(entry)
                uri != null -> onOpenFile(entry)
                else -> Timber.w("Folder entry is neither a directory nor a file: %s", entry.name)
            }
        },
        fallback = { glyphModifier ->
            Icon(
                painter = painterResource(ContentTypeCatalog.iconFor(type)),
                contentDescription = null,
                tint = if (ContentTypeCatalog.isMonochrome(type)) {
                    colorResource(ContentTypeCatalog.tintFor(type))
                } else {
                    Color.Unspecified
                },
                modifier = glyphModifier
            )
        }
    )
}
