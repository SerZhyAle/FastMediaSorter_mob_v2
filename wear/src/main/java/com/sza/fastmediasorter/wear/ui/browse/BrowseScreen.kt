package com.sza.fastmediasorter.wear.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
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
    val mediaTypeArg = navBackStackEntry?.arguments?.getString("mediaType")
    val sourceId = navBackStackEntry?.arguments?.getString("sourceId")
    val sourceName = navBackStackEntry?.arguments?.getString("sourceName")
    
    // Parse media type
    val mediaType = when (mediaTypeArg) {
        "music" -> MediaType.MUSIC
        "videos" -> MediaType.VIDEO
        "photos" -> MediaType.PHOTO
        else -> MediaType.MUSIC
    }
    
    // Initialize ViewModel with navigation args
    LaunchedEffect(mediaTypeArg, sourceId) {
        viewModel.setNavigationArgs(mediaType, sourceId, sourceName)
        viewModel.loadMediaFiles()
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val title = when (val screenTitle = viewModel.getScreenTitle()) {
        is ScreenTitle.Text -> screenTitle.value
        is ScreenTitle.Resource -> stringResource(screenTitle.id)
    }
    
    Timber.d("BrowseScreen composing with state: $uiState")
    
    when (val state = uiState) {
        is BrowseUiState.Loading -> {
            LoadingContent()
        }
        is BrowseUiState.Success -> {
            MediaListContent(
                title = title,
                files = state.files,
                mediaType = mediaType,
                onFileClick = { file ->
                    viewModel.selectFile(file)
                    navigateToPlayer(navController, file, mediaType)
                }
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

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
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
    onFileClick: (WearMediaFile) -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Title header
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
        
        // File list
        items(files, key = { it.id }) { file ->
            MediaFileChip(
                file = file,
                mediaType = mediaType,
                onClick = { onFileClick(file) }
            )
        }
    }
}

@Composable
private fun MediaFileChip(
    file: WearMediaFile,
    mediaType: MediaType,
    onClick: () -> Unit
) {
    // Determine icon based on file's actual mimeType
    val icon = when {
        file.mimeType?.startsWith("image/") == true -> "🖼️"
        file.mimeType?.startsWith("video/") == true -> "🎬"
        file.mimeType?.startsWith("audio/") == true -> "🎵"
        else -> when (mediaType) {
            MediaType.MUSIC -> "🎵"
            MediaType.VIDEO -> "🎬"
            MediaType.PHOTO -> "🖼️"
        }
    }
    
    // Show duration for audio/video, size for images
    val secondaryText = when {
        file.mimeType?.startsWith("image/") == true -> formatFileSize(file.size)
        file.mimeType?.startsWith("video/") == true -> formatDuration(file.duration)
        file.mimeType?.startsWith("audio/") == true -> formatDuration(file.duration)
        else -> ""
    }
    
    Chip(
        onClick = onClick,
        label = {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(text = "$icon $secondaryText")
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun EmptyContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
    Box(
        modifier = Modifier.fillMaxSize(),
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
                label = { Text("Retry") },
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
        file.mimeType?.startsWith("image/") == true -> "image_viewer/${file.id}"
        file.mimeType?.startsWith("video/") == true -> "video_player/${file.id}"
        file.mimeType?.startsWith("audio/") == true -> "audio_player/${file.id}"
        else -> when (mediaType) {
            MediaType.MUSIC -> "audio_player/${file.id}"
            MediaType.VIDEO -> "video_player/${file.id}"
            MediaType.PHOTO -> "image_viewer/${file.id}"
        }
    }
    Timber.d("Navigating to: $route for file: ${file.name} (mimeType: ${file.mimeType})")
    navController.navigate(route)
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        String.format("%d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
