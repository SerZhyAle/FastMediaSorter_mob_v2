package com.sza.fastmediasorter.wear.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
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
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

/**
 * Browse screen for displaying media files.
 * Shows a scrollable list of files based on media type (music, videos, photos).
 */
@Composable
fun BrowseScreen(
    navController: NavController,
    viewModel: BrowseViewModel = hiltViewModel()
) {
    // Extract navigation arguments
    val navBackStackEntry: NavBackStackEntry? = navController.currentBackStackEntry
    val mediaTypeArg = navBackStackEntry?.arguments?.getString(WearRoutes.ARG_MEDIA_TYPE)
    val sourceId = navBackStackEntry?.arguments?.getString(WearRoutes.ARG_SOURCE_ID)
    val sourceName = navBackStackEntry?.arguments?.getString(WearRoutes.ARG_SOURCE_NAME)
    
    // Parse media type
    val mediaType = when (mediaTypeArg) {
        "music" -> MediaType.MUSIC
        "videos" -> MediaType.VIDEO
        "photos" -> MediaType.PHOTO
        else -> {
            // S1829: three callers now pass this argument instead of one hard-coded value, so a route
            // that spells the type wrong is a real possibility rather than a theoretical one. The
            // default stays - a browse screen must still open - but it stops being silent: a wrong
            // route used to be indistinguishable from a deliberate music request.
            Timber.e("Browse: unknown media type argument '%s'; falling back to MUSIC", mediaTypeArg)
            MediaType.MUSIC
        }
    }
    
    // Initialize ViewModel with navigation args
    LaunchedEffect(mediaTypeArg, sourceId) {
        viewModel.setNavigationArgs(mediaType, sourceId, sourceName)
        viewModel.loadMediaFiles()
    }
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fileListViewMode by viewModel.fileListViewMode.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val title = when (val screenTitle = viewModel.getScreenTitle()) {
        is ScreenTitle.Text -> screenTitle.value
        is ScreenTitle.Resource -> stringResource(screenTitle.id)
    }
    
    Timber.d("BrowseScreen composing with state: $uiState")
    
    val listState = rememberScalingLazyListState()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        // Only the list branch scrolls, so only it has a position to indicate.
        positionIndicator = if (uiState is BrowseUiState.Success) {
            { PositionIndicator(listState) }
        } else {
            null
        }
    ) {
        when (val state = uiState) {
            is BrowseUiState.Loading -> {
                LoadingContent()
            }
            is BrowseUiState.Success -> {
                MediaListContent(
                    title = title,
                    files = state.files,
                    mediaType = mediaType,
                    listState = listState,
                    viewMode = fileListViewMode,
                    thumbnails = thumbnails,
                    actions = MediaFileActions(
                        onFileClick = { file ->
                            viewModel.selectFile(file)
                            navigateToPlayer(navController, file, mediaType)
                        },
                        onThumbnailNeeded = viewModel::thumbnailFor
                    )
                )
            }
            is BrowseUiState.Empty -> {
                EmptyContent(message = state.message)
            }
            is BrowseUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.loadMediaFiles() }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(wearScreenInsets()),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun MediaListContent(
    title: String,
    files: List<WearMediaFile>,
    mediaType: MediaType,
    listState: ScalingLazyListState,
    viewMode: WearViewMode,
    thumbnails: Map<Long, WearThumbnail>,
    actions: MediaFileActions
) {
    // The column count comes from the width this composable actually gets, never from the mode name -
    // the same rule the Resources page applies, so the two lists cannot drift apart.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        Timber.d("S1730: browse grid mode=$viewMode columns=$columns")
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets()
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }

            mediaFileItems(
                files = files,
                columns = columns,
                thumbnails = thumbnails,
                mediaType = mediaType,
                actions = actions
            )
        }
    }
}

@Composable
private fun EmptyContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(wearScreenInsets()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📂",
                style = MaterialTheme.typography.display2
            )
            Text(
                text = message,
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Timber.d("S1854: browse error retry chip shown")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(wearScreenInsets()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.display2
            )
            Text(
                text = message,
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
            Chip(
                onClick = onRetry,
                label = { Text(text = stringResource(R.string.retry)) },
                colors = ChipDefaults.primaryChipColors()
            )
        }
    }
}

private fun navigateToPlayer(
    navController: NavController,
    file: WearMediaFile,
    mediaType: MediaType
) {
    // Determine player based on file's actual mimeType, not the screen's mediaType
    val route = when {
        file.mimeType?.startsWith("image/") == true -> WearRoutes.imageViewer(file.id)
        file.mimeType?.startsWith("video/") == true -> WearRoutes.videoPlayer(file.id)
        file.mimeType?.startsWith("audio/") == true -> WearRoutes.audioPlayer(file.id)
        else -> when (mediaType) {
            MediaType.MUSIC -> WearRoutes.audioPlayer(file.id)
            MediaType.VIDEO -> WearRoutes.videoPlayer(file.id)
            MediaType.PHOTO -> WearRoutes.imageViewer(file.id)
        }
    }
    Timber.d("Navigating to: $route for file: ${file.name} (mimeType: ${file.mimeType})")
    navController.navigate(route)
}
