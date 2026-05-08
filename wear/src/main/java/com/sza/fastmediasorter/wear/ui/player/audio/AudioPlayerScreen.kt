package com.sza.fastmediasorter.wear.ui.player.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.sza.fastmediasorter.wear.R
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

    Timber.d("AudioPlayerScreen composing, isPlaying: ${uiState.isPlaying}")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
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
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Album art
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isAlbumArtLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp
                    )
                }
                uiState.albumArtUrl != null -> {
                    AsyncImage(
                        model = uiState.albumArtUrl,
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
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Track name
        Text(
            text = uiState.mediaFile?.name ?: "Unknown",
            style = MaterialTheme.typography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Progress indicator
        CircularProgressIndicator(
            progress = uiState.progress,
            modifier = Modifier.size(100.dp),
            strokeWidth = 4.dp,
            trackColor = Color.DarkGray
        )
        
        // Time display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = uiState.currentPositionFormatted,
                style = MaterialTheme.typography.caption3,
                color = Color.Gray
            )
            Text(
                text = uiState.durationFormatted,
                style = MaterialTheme.typography.caption3,
                color = Color.Gray
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val favoriteDesc = stringResource(R.string.wear_toggle_favorite)

        // Playback controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rewind button
            Button(
                onClick = onSeekBackward,
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("⏪", style = MaterialTheme.typography.body2)
            }
            
            // Play/Pause button
            Button(
                onClick = onPlayPause,
                modifier = Modifier.size(48.dp),
                colors = ButtonDefaults.primaryButtonColors()
            ) {
                Text(
                    text = if (uiState.isPlaying) "⏸" else "▶️",
                    style = MaterialTheme.typography.title2
                )
            }
            
            // Forward button
            Button(
                onClick = onSeekForward,
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("⏩", style = MaterialTheme.typography.body2)
            }

            // Favorite toggle button
            Button(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp),
                colors = if (isFavorite) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors()
            ) {
                Text(
                    text = if (isFavorite) "❤️" else "🤍",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.semantics { contentDescription = favoriteDesc }
                )
            }
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
