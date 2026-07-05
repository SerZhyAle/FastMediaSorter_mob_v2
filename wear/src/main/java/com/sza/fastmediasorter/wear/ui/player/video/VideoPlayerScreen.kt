package com.sza.fastmediasorter.wear.ui.player.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.ui.PlayerView
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import timber.log.Timber

/**
 * Video player screen for Wear OS.
 * Displays video with overlay controls and battery warning.
 */
@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // S0902: pause playback when the host activity stops (screen off / app backgrounded) -
    // onDispose only fires on navigation away, so without this the player kept running.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onHostStopped()
    }

    Timber.d("VideoPlayerScreen composing, isPlaying: ${uiState.isPlaying}")
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            uiState.showBatteryWarning -> {
                BatteryWarningDialog(
                    onDismiss = viewModel::dismissBatteryWarning
                )
            }
            uiState.error != null -> {
                ErrorContent(message = uiState.error!!)
            }
            uiState.isLoading && uiState.mediaFile == null -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            else -> {
                VideoPlayerContent(
                    uiState = uiState,
                    player = viewModel.getPlayer(),
                    onScreenTap = viewModel::onScreenTap,
                    onPlayPause = viewModel::togglePlayPause
                )
            }
        }
    }
}

@Composable
private fun BatteryWarningDialog(
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.display2
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.battery_warning),
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Chip(
            onClick = onDismiss,
            label = { Text("Continue") },
            colors = ChipDefaults.primaryChipColors()
        )
    }
}

@Composable
private fun VideoPlayerContent(
    uiState: VideoPlayerUiState,
    player: androidx.media3.exoplayer.ExoPlayer,
    onScreenTap: () -> Unit,
    onPlayPause: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    // S0725: keep a stable PlayerView reference so onDispose can detach the surface (player = null).
    // Media3 1.2.1 PlayerView does not unregister its ComponentListener on onDetachedFromWindow, so the
    // player would otherwise retain every disposed PlayerView (-> Context).
    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onScreenTap
            )
    ) {
        // Video surface
        AndroidView(
            factory = { playerView.also { it.player = player } },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.player = player
            }
        )
        
        // Loading indicator
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
            )
        }
        
        // Controls overlay
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            VideoControls(
                isPlaying = uiState.isPlaying,
                currentPosition = uiState.currentPositionFormatted,
                duration = uiState.durationFormatted,
                progress = uiState.progress,
                onPlayPause = onPlayPause
            )
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            // S0725: detach surface so the per-VM player does not retain the disposed PlayerView/Context.
            playerView.player = null
            Timber.d("VideoPlayerScreen disposed - player detached from surface")
        }
    }
}

@Composable
private fun VideoControls(
    isPlaying: Boolean,
    currentPosition: String,
    duration: String,
    progress: Float,
    onPlayPause: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Play/Pause button
        Button(
            onClick = onPlayPause,
            modifier = Modifier.size(56.dp),
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Text(
                text = if (isPlaying) "⏸" else "▶️",
                style = MaterialTheme.typography.title1
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress bar
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.size(80.dp),
            strokeWidth = 4.dp,
            trackColor = Color.DarkGray
        )
        
        // Time display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$currentPosition / $duration",
                style = MaterialTheme.typography.caption3,
                color = Color.White
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
