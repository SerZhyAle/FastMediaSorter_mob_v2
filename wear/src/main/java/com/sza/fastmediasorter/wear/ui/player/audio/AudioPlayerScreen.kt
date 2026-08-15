package com.sza.fastmediasorter.wear.ui.player.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.player.common.PlayerScaffold
import timber.log.Timber

/**
 * Audio player screen for Wear OS.
 * Shows album art, track info, progress, and playback controls.
 */
@Composable
fun AudioPlayerScreen(
    viewModel: AudioPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    // Hoisted out of the content so the scaffold drives its scroll indicator from the same state.
    val listState = rememberScalingLazyListState()

    // S0902: pause playback when the host activity stops (screen off / app backgrounded) -
    // onDispose only fires on navigation away, so without this the player kept running.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onHostStopped()
    }

    Timber.d("AudioPlayerScreen composing, isPlaying: ${uiState.isPlaying}")

    PlayerScaffold(
        positionIndicator = { PositionIndicator(listState) }
    ) {
        when {
            uiState.error != null -> {
                ErrorContent(message = uiState.error!!)
            }
            uiState.isLoading && uiState.mediaFile == null -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            else -> {
                AudioPlayerContent(
                    uiState = uiState,
                    isFavorite = isFavorite,
                    listState = listState,
                    onPlayPause = viewModel::togglePlayPause,
                    onSeekForward = viewModel::seekForward,
                    onSeekBackward = viewModel::seekBackward,
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }
        }
    }
}

@Composable
private fun AudioPlayerContent(
    uiState: AudioPlayerUiState,
    isFavorite: Boolean,
    listState: ScalingLazyListState,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    // S1683: this content is taller than a watch display, so it scrolls instead of being clipped -
    // the control row used to be pushed past the bottom edge, where it could not be reached at all.
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        item {
            AlbumArt(
                isLoading = uiState.isAlbumArtLoading,
                albumArtUrl = uiState.albumArtUrl
            )
        }
        item {
            Text(
                text = uiState.mediaFile?.name ?: "Unknown",
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            CircularProgressIndicator(
                progress = uiState.progress,
                modifier = Modifier.size(100.dp),
                strokeWidth = 4.dp,
                trackColor = Color.DarkGray
            )
        }
        item {
            PlaybackTimeRow(
                currentPosition = uiState.currentPositionFormatted,
                duration = uiState.durationFormatted
            )
        }
        item {
            PlaybackControls(
                isPlaying = uiState.isPlaying,
                isFavorite = isFavorite,
                onPlayPause = onPlayPause,
                onSeekForward = onSeekForward,
                onSeekBackward = onSeekBackward,
                onToggleFavorite = onToggleFavorite
            )
        }
    }
}

@Composable
private fun AlbumArt(
    isLoading: Boolean,
    albumArtUrl: String?
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp
                )
            }
            albumArtUrl != null -> {
                AsyncImage(
                    model = albumArtUrl,
                    contentDescription = "Album art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Text(
                    text = "🎵",
                    style = MaterialTheme.typography.title1
                )
            }
        }
    }
}

@Composable
private fun PlaybackTimeRow(
    currentPosition: String,
    duration: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = currentPosition,
            style = MaterialTheme.typography.caption3,
            color = Color.Gray
        )
        Text(
            text = duration,
            style = MaterialTheme.typography.caption3,
            color = Color.Gray
        )
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val favoriteDesc = stringResource(R.string.wear_toggle_favorite)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onSeekBackward,
            modifier = Modifier.size(36.dp),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text("⏪", style = MaterialTheme.typography.body2)
        }

        Button(
            onClick = onPlayPause,
            modifier = Modifier.size(48.dp),
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Text(
                text = if (isPlaying) "⏸" else "▶️",
                style = MaterialTheme.typography.title2
            )
        }

        Button(
            onClick = onSeekForward,
            modifier = Modifier.size(36.dp),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text("⏩", style = MaterialTheme.typography.body2)
        }

        Button(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp),
            colors = if (isFavorite) {
                ButtonDefaults.primaryButtonColors()
            } else {
                ButtonDefaults.secondaryButtonColors()
            }
        ) {
            Text(
                text = if (isFavorite) "❤️" else "🤍",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.semantics { contentDescription = favoriteDesc }
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
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
    }
}
