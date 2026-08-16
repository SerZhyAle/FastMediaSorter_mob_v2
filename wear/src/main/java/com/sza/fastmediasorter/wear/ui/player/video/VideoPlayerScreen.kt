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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.semantics.contentDescription
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
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSteps
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

    WearScreenScaffold(contentPadding = PaddingValues(0.dp)) {
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
                    onPlayPause = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipToNext,
                    onSkipPrevious = viewModel::skipToPrevious,
                    onRotaryStep = { step ->
                        // S1683: same binding as audio, per strategic 6.2 - the bezel moves inside the
                        // file, and the file is changed by the buttons only.
                        if (step > 0) {
                            viewModel.seekForward()
                        } else {
                            viewModel.seekBackward()
                        }
                    }
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
            label = { Text(stringResource(R.string.wear_video_continue)) },
            colors = ChipDefaults.primaryChipColors()
        )
    }
}

@Composable
private fun VideoPlayerContent(
    uiState: VideoPlayerUiState,
    player: androidx.media3.exoplayer.ExoPlayer,
    onScreenTap: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onRotaryStep: (Int) -> Unit
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
            // The rotary binding is a separate input from the tap: rotation moves the position and
            // leaves the overlay alone, so the bezel does not have to reveal controls to be useful.
            .rotaryActionSteps(onRotaryStep)
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
                uiState = uiState,
                onPlayPause = onPlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious
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
private fun PlayPauseButton(
    isPlaying: Boolean,
    description: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors = ButtonDefaults.primaryButtonColors()
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = description,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun VideoControls(
    uiState: VideoPlayerUiState,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val previousDesc = stringResource(R.string.wear_previous_file)
    val nextDesc = stringResource(R.string.wear_next_file)
    // Names the action the press performs and follows the state, so playing versus paused is
    // announced rather than only drawn.
    val playPauseDesc = stringResource(if (uiState.isPlaying) R.string.pause else R.string.play)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            // The scrim stays full-bleed; only the controls are inset, so nothing lands where a
            // round screen has already curved away.
            .padding(wearScreenInsets()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.hasSet) {
                Button(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = previousDesc,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            PlayPauseButton(
                isPlaying = uiState.isPlaying,
                description = playPauseDesc,
                onClick = onPlayPause
            )

            if (uiState.hasSet) {
                Button(
                    onClick = onSkipNext,
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = nextDesc,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        CircularProgressIndicator(
            progress = uiState.progress,
            modifier = Modifier.size(80.dp),
            strokeWidth = 4.dp,
            trackColor = Color.DarkGray
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${uiState.currentPositionFormatted} / ${uiState.durationFormatted}",
                style = MaterialTheme.typography.caption3,
                color = Color.White
            )
        }

        if (uiState.hasSet) {
            Text(
                text = uiState.positionText,
                style = MaterialTheme.typography.caption3,
                color = Color.Gray
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
