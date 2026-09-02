package com.sza.fastmediasorter.wear.ui.player.video

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.StreamChannelReason
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCommandButton
import com.sza.fastmediasorter.wear.ui.player.common.PlayerSeekActions
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSteps
import timber.log.Timber

/** Wear's minimum comfortable touch target - the same 48 dp the transport buttons use. */
/** The play/pause control is the one command drawn larger than the shared default. */
private val PLAY_PAUSE_TARGET_SIZE = 56.dp
private val SECONDARY_ROW_SPACING = 4.dp

private val PROGRESS_BAR_HEIGHT = 4.dp
private val PROGRESS_BAR_TOUCH_HEIGHT = 24.dp
private val PROGRESS_BAR_SPACING = 4.dp
private const val PROGRESS_BAR_CORNER_PERCENT = 50

private data class VideoPlayerActions(
    val onScreenTap: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSkipNext: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val seek: PlayerSeekActions,
    val onRotaryStep: (Int) -> Unit,
    val onToggleScaleMode: () -> Unit,
    val onToggleShuffle: () -> Unit,
    val onPanDelta: (Float, Float) -> Unit,
    val onToggleFavorite: () -> Unit
)

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

    KeepScreenOnEffect(enabled = uiState.isPlaying)

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
                    actions = VideoPlayerActions(
                        onScreenTap = viewModel::onScreenTap,
                        onPlayPause = viewModel::togglePlayPause,
                        onSkipNext = viewModel::skipToNext,
                        onSkipPrevious = viewModel::skipToPrevious,
                        seek = PlayerSeekActions(
                            onSeekTo = viewModel::seekTo,
                            onSeekBackward = viewModel::seekBackward,
                            onSeekForward = viewModel::seekForward
                        ),
                        onRotaryStep = { step ->
                            // S2140: the bezel now matches audio's volume binding (S1701) instead of
                            // seeking - seeking moved to a long press on the previous/next buttons.
                            viewModel.onVolumeStep(up = step > 0)
                        },
                        onToggleScaleMode = viewModel::toggleScaleMode,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onPanDelta = viewModel::onPanDelta,
                        onToggleFavorite = viewModel::toggleFavorite
                    )
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
private fun VideoSurfaceHost(
    playerView: PlayerView,
    player: androidx.media3.exoplayer.ExoPlayer,
    scaleMode: VideoScaleMode,
    panOffsetX: Float,
    panOffsetY: Float,
    onPanDelta: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val resizeMode = if (scaleMode == VideoScaleMode.CROP_PAN) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    AndroidView(
        factory = {
            playerView.apply {
                this.player = player
                this.resizeMode = resizeMode
            }
        },
        modifier = modifier
            .graphicsLayer {
                translationX = panOffsetX
                translationY = panOffsetY
            }
            .pointerInput(scaleMode) {
                if (scaleMode == VideoScaleMode.CROP_PAN) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onPanDelta(dragAmount.x, dragAmount.y)
                    }
                }
            },
        update = { view ->
            view.player = player
            view.resizeMode = resizeMode
        }
    )
}

@Composable
private fun VideoPlayerContent(
    uiState: VideoPlayerUiState,
    player: androidx.media3.exoplayer.ExoPlayer,
    actions: VideoPlayerActions
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
            .rotaryActionSteps(actions.onRotaryStep)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = actions.onScreenTap
            )
    ) {
        VideoSurfaceHost(
            playerView = playerView,
            player = player,
            scaleMode = uiState.scaleMode,
            panOffsetX = uiState.panOffsetX,
            panOffsetY = uiState.panOffsetY,
            onPanDelta = actions.onPanDelta,
            modifier = Modifier.fillMaxSize()
        )

        uiState.channelReason?.let { reason ->
            StreamChannelNotice(
                reason = reason,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(wearScreenInsets())
            )
        }

        // Loading indicator
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
            )
        }

        // Controls overlay.
        // S2250: the setting removes the fade, not the panel - the plain branch reaches the same
        // visible state in one frame, and hiding it still hides VideoControls with it.
        LaunchedEffect(uiState.animationsDisabled) {
            if (uiState.animationsDisabled) Timber.d("S2250: Wear video controls transition skipped")
        }
        when {
            !uiState.animationsDisabled -> AnimatedVisibility(
                visible = uiState.showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                VideoControls(
                    uiState = uiState,
                    actions = actions
                )
            }

            uiState.showControls -> Box(modifier = Modifier.align(Alignment.Center)) {
                VideoControls(
                    uiState = uiState,
                    actions = actions
                )
            }
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
    PlayerCommandButton(
        onClick = onClick,
        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        contentDescription = description,
        modifier = Modifier.size(PLAY_PAUSE_TARGET_SIZE),
        checked = true
    )
}

@Composable
private fun VideoActionButtons(
    isPlaying: Boolean,
    scaleMode: VideoScaleMode,
    isShuffleEnabled: Boolean,
    actions: VideoPlayerActions
) {
    val previousDesc = stringResource(R.string.wear_previous_file)
    val nextDesc = stringResource(R.string.wear_next_file)
    val playPauseDesc = stringResource(if (isPlaying) R.string.pause else R.string.play)
    val shuffleDesc = stringResource(
        if (isShuffleEnabled) R.string.wear_shuffle_on else R.string.wear_shuffle_off
    )
    // S2140: seeking moved off the bezel onto a long press here, and the labels are what TalkBack reads
    // in place of "double tap and hold".
    val seekBackwardDesc = stringResource(R.string.wear_seek_backward)
    val seekForwardDesc = stringResource(R.string.wear_seek_forward)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // S2140: drawn whether or not a set exists, unlike before. The bezel used to carry seeking and
        // did not care about the set, so hiding these two with the set cost nothing; now they carry it,
        // and hiding them would take seeking away from exactly the single file and the stream that the
        // bezel used to serve. The tap is the part that stays conditional - skipToPrevious/skipToNext
        // already return early on a set of one, so a tap without a set does nothing rather than wrong.
        PlayerCommandButton(
            onClick = actions.onSkipPrevious,
            icon = Icons.Filled.SkipPrevious,
            contentDescription = previousDesc,
            onLongClick = actions.seek.onSeekBackward,
            onLongClickLabel = seekBackwardDesc
        )

        PlayPauseButton(
            isPlaying = isPlaying,
            description = playPauseDesc,
            onClick = actions.onPlayPause
        )

        PlayerCommandButton(
            onClick = actions.onSkipNext,
            icon = Icons.Filled.SkipNext,
            contentDescription = nextDesc,
            onLongClick = actions.seek.onSeekForward,
            onLongClickLabel = seekForwardDesc
        )

        val scaleIcon = if (scaleMode == VideoScaleMode.CROP_PAN) {
            Icons.Filled.AspectRatio
        } else {
            Icons.Filled.CropFree
        }
        PlayerCommandButton(
            onClick = actions.onToggleScaleMode,
            icon = scaleIcon,
            contentDescription = stringResource(R.string.wear_scale_mode),
            checked = scaleMode == VideoScaleMode.CROP_PAN
        )

        PlayerCommandButton(
            onClick = actions.onToggleShuffle,
            // Shape as well as tint - the accessibility constraint asks for two signals.
            icon = if (isShuffleEnabled) Icons.Filled.Shuffle else Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = shuffleDesc,
            checked = isShuffleEnabled
        )
    }
}

/**
 * S1954: the mark lives on its own row rather than beside the transport buttons, because that row is
 * already four controls wide on a round screen. The audio player made the same split for the same
 * reason (S1701), so the star sits in the same place in both players.
 *
 * The filled and outlined hearts differ in shape, not only in colour, so the state survives a screen
 * that renders the highlight faintly.
 */
@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    val description = stringResource(
        if (isFavorite) R.string.wear_player_favorite_remove else R.string.wear_player_favorite_add
    )
    PlayerCommandButton(
        onClick = onToggleFavorite,
        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = description,
        checked = isFavorite
    )
}

@Composable
private fun VideoControls(
    uiState: VideoPlayerUiState,
    actions: VideoPlayerActions
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            // The scrim stays full-bleed; only the controls are inset, so nothing lands where a
            // round screen has already curved away.
            .padding(wearScreenInsets()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // S2140: shown only while the bezel is being turned and for a moment after, same readout shape
        // and the same string audio already uses (strategic 3.2 - one key, not a duplicate per screen).
        // It needs no visibility rule of its own: this whole column is already inside whichever
        // branch renders the panel, so hiding the panel hides this with it.
        if (uiState.isVolumeVisible) {
            val readout = stringResource(
                R.string.wear_audio_volume_level,
                uiState.volumeLevel,
                uiState.volumeMax,
            )
            Text(
                text = readout,
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = readout },
            )
        }
        PlaybackTimeRow(
            currentPosition = uiState.currentPositionFormatted,
            duration = uiState.durationFormatted,
            progress = uiState.progress,
            durationMs = uiState.durationMs,
            onSeekTo = actions.seek.onSeekTo
        )

        Spacer(modifier = Modifier.height(4.dp))

        VideoActionButtons(
            isPlaying = uiState.isPlaying,
            scaleMode = uiState.scaleMode,
            isShuffleEnabled = uiState.isShuffleEnabled,
            actions = actions
        )

        Spacer(modifier = Modifier.height(SECONDARY_ROW_SPACING))

        FavoriteButton(
            isFavorite = uiState.isFavorite,
            onToggleFavorite = actions.onToggleFavorite
        )

        if (uiState.hasSet) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = uiState.positionText,
                style = MaterialTheme.typography.caption3,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun StreamChannelNotice(
    reason: StreamChannelReason,
    modifier: Modifier = Modifier
) {
    val messageRes = reason.toMessageRes() ?: return
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.primary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.85f))
            .padding(6.dp)
    )
}

@StringRes
private fun StreamChannelReason.toMessageRes(): Int? = when (this) {
    StreamChannelReason.NARROW_LINK -> R.string.wear_stream_channel_narrow
    StreamChannelReason.NO_LINK -> R.string.wear_stream_channel_offline
    StreamChannelReason.UNVALIDATED_LINK -> R.string.wear_stream_channel_unverified
    StreamChannelReason.BANDWIDTH_UNKNOWN -> null
}

@Composable
private fun PlaybackTimeRow(
    currentPosition: String,
    duration: String,
    progress: Float,
    durationMs: Long,
    onSeekTo: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PROGRESS_BAR_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = currentPosition,
            style = MaterialTheme.typography.caption3,
            color = Color.Gray
        )
        SeekBar(
            progress = progress,
            durationMs = durationMs,
            onSeekTo = onSeekTo
        )
        Text(
            text = duration,
            style = MaterialTheme.typography.caption3,
            color = Color.Gray
        )
    }
}

@Composable
private fun RowScope.SeekBar(
    progress: Float,
    durationMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val seekDesc = stringResource(R.string.wear_seek_drag)
    var barWidthPx by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(percent = PROGRESS_BAR_CORNER_PERCENT)

    Box(
        modifier = Modifier
            .weight(1f)
            .height(PROGRESS_BAR_TOUCH_HEIGHT)
            .onSizeChanged { barWidthPx = it.width }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (durationMs > 0 && barWidthPx > 0) {
                        val fraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                        onSeekTo((durationMs * fraction).toLong())
                    }
                }
            }
            .semantics { contentDescription = seekDesc },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRESS_BAR_HEIGHT)
                .clip(shape)
                .background(Color.DarkGray)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(PROGRESS_BAR_HEIGHT)
                .clip(shape)
                .background(MaterialTheme.colors.primary)
        )
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
