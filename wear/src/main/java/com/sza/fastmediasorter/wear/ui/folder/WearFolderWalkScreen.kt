package com.sza.fastmediasorter.wear.ui.folder

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearFolderEntry
import com.sza.fastmediasorter.wear.domain.model.contentTypeForEntry
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.common.WearListMetrics
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import timber.log.Timber

private val TITLE_PADDING_VERTICAL = 12.dp

/**
 * S2201: the walk over the watch's own storage, one level at a time.
 *
 * @param onOpenFile receives the tapped file and its mime type. The screen resolves no player
 * itself: which destination renders a kind is the navigation host's answer already, and a second
 * copy of that rule here would be free to disagree with it.
 * @param onExit called when Back is pressed at the level the walk started on, so the trail is spent
 * before the screen is - a walk that left from depth three would be indistinguishable from a crash.
 */
@Composable
fun WearFolderWalkScreen(
    onOpenFile: (Uri, String?) -> Unit,
    onExit: () -> Unit,
    viewModel: WearFolderWalkViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = true) {
        if (!viewModel.navigateUp()) {
            onExit()
        }
    }

    val listState = rememberScalingLazyListState()

    WearScreenScaffold(
        // The list insets itself instead, so rows scroll under the rim rather than inside a
        // padded viewport that clips them.
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        when (val current = state) {
            is WearFolderWalkUiState.Loading -> CircularProgressIndicator()

            is WearFolderWalkUiState.Empty -> WearStateBlock(
                kind = WearStateKind.EMPTY,
                // No Retry: the listing that came back empty already succeeded, so repeating it
                // returns the same empty level.
                onBack = { if (current.canGoUp) viewModel.navigateUp() else onExit() }
            )

            is WearFolderWalkUiState.Content -> FolderWalkList(
                content = current,
                listState = listState,
                onOpenFolder = viewModel::openFolder,
                onOpenFile = onOpenFile,
                onLoadMore = viewModel::loadMore
            )
        }
    }
}

@Composable
private fun FolderWalkList(
    content: WearFolderWalkUiState.Content,
    listState: ScalingLazyListState,
    onOpenFolder: (WearFolderEntry) -> Unit,
    onOpenFile: (Uri, String?) -> Unit,
    onLoadMore: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = wearScreenInsets()
    ) {
        item {
            LevelTitle(title = content.title)
        }

        items(content.entries) { entry ->
            FolderWalkRow(
                entry = entry,
                onOpenFolder = onOpenFolder,
                onOpenFile = onOpenFile
            )
        }

        // The last row rather than an edge-triggered fetch: a window is bounded on purpose (ADR-5),
        // and a wearer who has reached the end is the one who decides whether to read further.
        if (content.canLoadMore) {
            item {
                Chip(
                    onClick = onLoadMore,
                    label = { Text(text = stringResource(R.string.wear_folder_load_more)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
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
private fun FolderWalkRow(
    entry: WearFolderEntry,
    onOpenFolder: (WearFolderEntry) -> Unit,
    onOpenFile: (Uri, String?) -> Unit
) {
    val type = contentTypeForEntry(entry.mimeType, entry.isDirectory)
    Chip(
        onClick = {
            val uri = entry.uri
            when {
                entry.isDirectory -> onOpenFolder(entry)
                uri != null -> onOpenFile(uri, entry.mimeType)
                // The entry model's invariant makes this unreachable, and a row with neither an
                // address nor a uri is not a tap target - leading nowhere silently would read as
                // the walk having broken.
                else -> Timber.w("Folder entry is neither a directory nor a file: %s", entry.name)
            }
        },
        label = { Text(text = entry.name) },
        icon = {
            Icon(
                painter = painterResource(ContentTypeCatalog.iconFor(type)),
                contentDescription = null,
                tint = if (ContentTypeCatalog.isMonochrome(type)) {
                    colorResource(ContentTypeCatalog.tintFor(type))
                } else {
                    Color.Unspecified
                },
                modifier = Modifier.size(WearListMetrics.LeadingIconNormal)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = entry.name },
        colors = ChipDefaults.primaryChipColors()
    )
}
