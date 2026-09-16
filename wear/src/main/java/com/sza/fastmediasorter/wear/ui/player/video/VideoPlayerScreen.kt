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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackMode
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.common.WearAction
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearIsCompactScreen
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.player.common.PRIMARY_ROW_COLUMNS
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCastMessage
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCommandButton
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCommandGrid
import com.sza.fastmediasorter.wear.ui.player.common.PlayerDialogVisibilities
import com.sza.fastmediasorter.wear.ui.player.common.PlayerDialogsHost
import com.sza.fastmediasorter.wear.ui.player.common.PlayerOverflowMenu
import com.sza.fastmediasorter.wear.ui.player.common.PlayerProgressRing
import com.sza.fastmediasorter.wear.ui.player.common.PlayerSeekActions
import com.sza.fastmediasorter.wear.ui.player.common.closingWith
import com.sza.fastmediasorter.wear.ui.player.common.playerMenuAction
import com.sza.fastmediasorter.wear.ui.player.common.playerPrimaryRowColumns
import com.sza.fastmediasorter.wear.ui.player.common.rememberPlayerFileActionEntries
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSteps
import com.sza.fastmediasorter.wear.ui.player.common.secondaryRowColumns
import timber.log.Timber

/** Wear's minimum comfortable touch target - the same 48 dp the transport buttons use. */
/** The play/pause control is the one command drawn larger than the shared default. */
private val SECONDARY_ROW_SPACING = 4.dp

/** Separates the time row from the transport row, on the branch that still draws a time row. */
private val TIME_ROW_SPACING = 4.dp

private val PROGRESS_BAR_HEIGHT = 4.dp
private val PROGRESS_BAR_TOUCH_HEIGHT = 24.dp
private val PROGRESS_BAR_SPACING = 4.dp
private const val PROGRESS_BAR_CORNER_PERCENT = 50

private data class VideoPlayerActions(
    val onBack: () -> Unit,
    val onScreenTap: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSkipNext: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val seek: PlayerSeekActions,
    val onRotaryStep: (Int) -> Unit,
    val onToggleScaleMode: () -> Unit,
    val onTogglePlaybackMode: () -> Unit,
    val onPanDelta: (Float, Float) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onTogglePin: () -> Unit,
    /** S3118: the file operations as menu entries, built from the file capability policy's set. */
    val fileActions: List<WearAction>,
    val onToggleCast: () -> Unit,
    /** The phone.s reported session, which decides only the wording of the one cast entry (S2531). */
    val isCasting: Boolean
)

/** Every command the panel and the menu can issue, bound to the one view model behind them. */
private fun videoPlayerActions(
    viewModel: VideoPlayerViewModel,
    onBack: () -> Unit,
    fileActions: List<WearAction>,
    isCasting: Boolean
): VideoPlayerActions = VideoPlayerActions(
    onBack = onBack,
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
        // S2140: the bezel now matches audio's volume binding (S1701) instead of seeking - seeking
        // moved to a long press on the previous/next buttons.
        viewModel.onVolumeStep(up = step > 0)
    },
    onToggleScaleMode = viewModel::toggleScaleMode,
    onTogglePlaybackMode = viewModel::togglePlaybackMode,
    onPanDelta = viewModel::onPanDelta,
    onToggleFavorite = viewModel::toggleFavorite,
    onTogglePin = viewModel::togglePin,
    fileActions = fileActions,
    onToggleCast = viewModel::toggleCast,
    isCasting = isCasting
)

/**
 * Video player screen for Wear OS.
 * Displays video with overlay controls and battery warning.
 */
@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val castState by viewModel.castManager.castState.collectAsStateWithLifecycle()
    val isCasting = castState.isCasting

    // S3118: this player draws the file operations as entries of its own menu, so the file-action
    // dialog never opens here and the flag only ever reads false. It is still passed to the shared
    // host, which owns the rename, delete and receiver dialogs the entries do open.
    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReceivers by remember { mutableStateOf(false) }

    val dialogVisibilities = PlayerDialogVisibilities(
        showActions = showActions,
        showDeleteConfirm = showDeleteConfirm,
        showReceivers = showReceivers,
        onActionsVisibilityChange = { showActions = it },
        onDeleteVisibilityChange = { showDeleteConfirm = it },
        onReceiversVisibilityChange = { showReceivers = it }
    )
    val fileActions = rememberPlayerFileActionEntries(
        operations = viewModel.fileOperations,
        visibilities = dialogVisibilities,
        currentFileName = uiState.mediaFile?.name
    )

    LaunchedEffect(uiState.closeScreen) {
        if (uiState.closeScreen) {
            onBack()
        }
    }

    // S0902: pause playback when the host activity stops (screen off / app backgrounded) -
    // onDispose only fires on navigation away, so without this the player kept running.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onHostStopped()
    }

    WearScreenScaffold(
        showTimeText = uiState.showControls,
        contentPadding = PaddingValues(0.dp)
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
                    actions = videoPlayerActions(
                        viewModel = viewModel,
                        onBack = onBack,
                        fileActions = fileActions,
                        isCasting = isCasting
                    )
                )
            }
        }
    }

    PlayerDialogsHost(
        operations = viewModel.fileOperations,
        visibilities = dialogVisibilities,
        currentFileName = uiState.mediaFile?.name
    )

    PlayerCastMessage(viewModel.castManager)
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
    // Held here rather than inside the controls panel: the panel comes and goes on a tap, and a menu
    // that vanished with it would take the wearer's half-made choice with it.
    var showMenu by rememberSaveable { mutableStateOf(false) }

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

        VideoControlsOverlay(
            uiState = uiState,
            actions = actions,
            onOpenMenu = {
                Timber.d("S3118: video menu opened")
                showMenu = true
            }
        )
    }

    if (showMenu) {
        PlayerOverflowMenu(
            actions = videoMenuActions(
                uiState = uiState,
                actions = actions,
                onDismiss = { showMenu = false }
            ),
            onDismiss = { showMenu = false }
        )
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
    onClick: () -> Unit,
    size: Dp
) {
    PlayerCommandButton(
        onClick = onClick,
        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        contentDescription = description,
        size = size,
        checked = true,
        iconTint = colorResource(ContentTypeCatalog.tintFor(WearContentType.VIDEO))
    )
}

/**
 * S2766: three commands, the same set and for the same reason as the audio player - previous,
 * play/pause and next are what Google names primary, and three 48 dp cells are what the small round
 * glass holds. The playback mode moved to the player menu. Below the breakpoint the position rides a
 * ring around the play button, because the compact panel has no time row for it.
 */
@Composable
private fun VideoActionButtons(
    isPlaying: Boolean,
    playbackMode: WearPlaybackMode,
    progress: Float,
    actions: VideoPlayerActions
) {
    val previousDesc = stringResource(R.string.wear_previous_file)
    val nextDesc = stringResource(R.string.wear_next_file)
    val playPauseDesc = stringResource(if (isPlaying) R.string.pause else R.string.play)
    val seekBackwardDesc = stringResource(R.string.wear_seek_backward)
    val seekForwardDesc = stringResource(R.string.wear_seek_forward)
    val ringed = wearIsCompactScreen()
    // S2803: the ORIGINAL view restores the row of four - previous, play/pause, playback mode, next,
    // the composition the pre-S2766 tree drew - with the bare play button. STORE keeps the ring.
    val restored = playerPrimaryRowColumns() != PRIMARY_ROW_COLUMNS
    val playbackModeIcon = when (playbackMode) {
        WearPlaybackMode.SEQUENTIAL -> Icons.AutoMirrored.Filled.Sort
        WearPlaybackMode.SHUFFLE -> Icons.Filled.Shuffle
        WearPlaybackMode.LOOP -> Icons.Filled.Repeat
    }
    val playbackModeDesc = stringResource(
        when (playbackMode) {
            WearPlaybackMode.SEQUENTIAL -> R.string.wear_playback_mode_sequential
            WearPlaybackMode.SHUFFLE -> R.string.wear_playback_mode_shuffle
            WearPlaybackMode.LOOP -> R.string.wear_playback_mode_loop
        }
    )

    PlayerCommandGrid(columns = playerPrimaryRowColumns()) { targetSize ->
        PlayerCommandButton(
            onClick = actions.onSkipPrevious,
            icon = Icons.Filled.SkipPrevious,
            contentDescription = previousDesc,
            size = targetSize,
            onLongClick = actions.seek.onSeekBackward,
            onLongClickLabel = seekBackwardDesc
        )

        val playPause: @Composable () -> Unit = {
            PlayPauseButton(
                isPlaying = isPlaying,
                description = playPauseDesc,
                onClick = actions.onPlayPause,
                size = targetSize
            )
        }
        if (ringed && !restored) {
            PlayerProgressRing(progress = progress, content = playPause)
        } else {
            playPause()
        }

        if (restored) {
            PlayerCommandButton(
                onClick = actions.onTogglePlaybackMode,
                icon = playbackModeIcon,
                contentDescription = playbackModeDesc,
                size = targetSize,
                checked = playbackMode != WearPlaybackMode.SEQUENTIAL
            )
        }

        PlayerCommandButton(
            onClick = actions.onSkipNext,
            icon = Icons.Filled.SkipNext,
            contentDescription = nextDesc,
            size = targetSize,
            onLongClick = actions.seek.onSeekForward,
            onLongClickLabel = seekForwardDesc
        )
    }
}

/**
 * S1954: the mark lives on its own row rather than beside the transport buttons, because that row is
 * already four controls wide on a round screen. The audio player made the same split for the same
 * reason (S1701), so the star sits in the same place in both players.
 *
 * The filled and outlined hearts differ in shape, not only in colour, so the state survives a screen
 * that renders the highlight faintly. S2497: a stream swaps the heart for a pin - the list orders by
 * the same mark, so "favourite" would name an action the wearer cannot get - and keeps the same
 * filled/outlined split for the same reason.
 */
@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    size: Dp
) {
    val description = stringResource(
        if (isFavorite) {
            R.string.wear_player_favorite_remove
        } else {
            R.string.wear_player_favorite_add
        }
    )
    PlayerCommandButton(
        onClick = onToggleFavorite,
        icon = if (isFavorite) {
            Icons.Filled.Favorite
        } else {
            Icons.Filled.FavoriteBorder
        },
        contentDescription = description,
        size = size,
        checked = isFavorite
    )
}

@Composable
private fun VideoControls(
    uiState: VideoPlayerUiState,
    actions: VideoPlayerActions,
    onOpenMenu: () -> Unit
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
        // S2766: below the breakpoint the panel cannot afford this row - it is what closes the
        // column's deficit at 192 dp - so the position moves onto the ring around the play button.
        if (!wearIsCompactScreen()) {
            PlaybackTimeRow(
                currentPosition = uiState.currentPositionFormatted,
                duration = uiState.durationFormatted,
                progress = uiState.progress,
                durationMs = uiState.durationMs,
                onSeekTo = actions.seek.onSeekTo
            )

            Spacer(modifier = Modifier.height(TIME_ROW_SPACING))
        }

        VideoActionButtons(
            isPlaying = uiState.isPlaying,
            playbackMode = uiState.playbackMode,
            progress = uiState.progress,
            actions = actions
        )

        Spacer(modifier = Modifier.height(SECONDARY_ROW_SPACING))

        VideoControlsSecondaryRow(
            uiState = uiState,
            actions = actions,
            onOpenMenu = onOpenMenu
        )
    }
}

/**
 * S2250: the setting removes the fade, not the panel - the plain branch reaches the same visible
 * state in one frame, and hiding it still hides [VideoControls] with it.
 */
@Composable
private fun BoxScope.VideoControlsOverlay(
    uiState: VideoPlayerUiState,
    actions: VideoPlayerActions,
    onOpenMenu: () -> Unit
) {
    when {
        !uiState.animationsDisabled -> AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            VideoControls(uiState = uiState, actions = actions, onOpenMenu = onOpenMenu)
        }

        uiState.showControls -> Box(modifier = Modifier.align(Alignment.Center)) {
            VideoControls(uiState = uiState, actions = actions, onOpenMenu = onOpenMenu)
        }
    }
}

/** The glyph that states which scale mode is in force, shared by the panel button and the menu. */
private fun scaleModeIcon(scaleMode: VideoScaleMode): ImageVector =
    if (scaleMode == VideoScaleMode.CROP_PAN) Icons.Filled.AspectRatio else Icons.Filled.CropFree

/**
 * Everything the video panel's rows shed, plus the file operations, in one list.
 *
 * S2766: the same menu the audio player opens, plus the scale mode, which exists only here.
 *
 * S3118: a command the panel draws as a button in the view in force is left out here - offering it
 * twice makes the menu the second answer to a question the row already answers - so the playback
 * mode and the scale mode appear only under STORE, where neither has a cell. The file operations are
 * appended rather than hidden behind an entry opening their own dialog, so one opening reaches a
 * rename; they are still the capability policy's set, read through the shared builder. Screen-off is
 * gone from the video player altogether: dimming the glass during a video hides the one thing the
 * screen is open for. It stays in the audio players, which is where it means something.
 */
@Composable
private fun videoMenuActions(
    uiState: VideoPlayerUiState,
    actions: VideoPlayerActions,
    onDismiss: () -> Unit
): List<WearAction> {
    val playbackModeIcon = when (uiState.playbackMode) {
        WearPlaybackMode.SEQUENTIAL -> Icons.AutoMirrored.Filled.Sort
        WearPlaybackMode.SHUFFLE -> Icons.Filled.Shuffle
        WearPlaybackMode.LOOP -> Icons.Filled.Repeat
    }
    val playbackModeLabel = stringResource(
        when (uiState.playbackMode) {
            WearPlaybackMode.SEQUENTIAL -> R.string.wear_playback_mode_sequential
            WearPlaybackMode.SHUFFLE -> R.string.wear_playback_mode_shuffle
            WearPlaybackMode.LOOP -> R.string.wear_playback_mode_loop
        }
    )
    val favoriteLabel = stringResource(
        if (uiState.isFavorite) {
            R.string.wear_player_favorite_remove
        } else {
            R.string.wear_player_favorite_add
        }
    )
    val pinLabel = stringResource(
        if (uiState.isPinned) R.string.wear_player_stream_unpin else R.string.wear_player_stream_pin
    )
    val scaleLabel = stringResource(R.string.wear_scale_mode)
    // S2531: the wording carries the state, not a colour - strategic 3.2 accessibility.
    val castLabel = stringResource(
        if (actions.isCasting) R.string.wear_cast_stop else R.string.wear_cast_send
    )
    val castIcon = if (actions.isCasting) Icons.Filled.CastConnected else Icons.Filled.Cast
    val showFavorite = wearIsCompactScreen()
    val onPanel = playerPrimaryRowColumns() != PRIMARY_ROW_COLUMNS

    return buildList {
        if (!onPanel) {
            add(
                playerMenuAction(
                    playbackModeLabel,
                    playbackModeIcon,
                    onDismiss,
                    actions.onTogglePlaybackMode
                )
            )
        }
        if (showFavorite) {
            val icon = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
            add(playerMenuAction(favoriteLabel, icon, onDismiss, actions.onToggleFavorite))
        }
        if (uiState.isStream) {
            val icon = if (uiState.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
            add(playerMenuAction(pinLabel, icon, onDismiss, actions.onTogglePin))
        }
        if (!onPanel) {
            add(
                playerMenuAction(
                    scaleLabel,
                    scaleModeIcon(uiState.scaleMode),
                    onDismiss,
                    actions.onToggleScaleMode
                )
            )
        }
        add(playerMenuAction(castLabel, castIcon, onDismiss, actions.onToggleCast))
        // Last, and last for the same reason the file-action dialog puts them last: delete closes
        // the list, and the outer rows of a round screen are the easiest to reach by accident.
        addAll(actions.fileActions.map { entry -> entry.closingWith(onDismiss) })
    }
}

/**
 * S2766: back, the favourite where a third slot exists, and the menu button - the same two-or-three
 * composition the audio player draws. The scale mode and the stream pin left this row for the menu.
 *
 * S3118: the ORIGINAL view's third slot holds the scale mode. It held a second "more" button - file
 * operations for a file, the pin for a stream - and two identical three-dot glyphs in one row gave
 * the wearer no way to tell which list a tap would open. Both of those commands stay reachable as
 * menu entries; the scale mode is the one this row can show as a state, because its glyph differs
 * per mode.
 */
@Composable
private fun VideoControlsSecondaryRow(
    uiState: VideoPlayerUiState,
    actions: VideoPlayerActions,
    onOpenMenu: () -> Unit
) {
    val menuDesc = stringResource(R.string.wear_file_op_actions)
    val scaleDesc = stringResource(R.string.wear_scale_mode)
    val restored = playerPrimaryRowColumns() != PRIMARY_ROW_COLUMNS

    PlayerCommandGrid(columns = secondaryRowColumns()) { targetSize ->
        PlayerCommandButton(
            onClick = actions.onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.wear_navigate_back),
            size = targetSize
        )

        if (restored || !wearIsCompactScreen()) {
            FavoriteButton(
                isFavorite = uiState.isFavorite,
                onToggleFavorite = actions.onToggleFavorite,
                size = targetSize
            )
        }

        if (restored) {
            PlayerCommandButton(
                onClick = {
                    Timber.d("S3118: scale mode toggled from the command row")
                    actions.onToggleScaleMode()
                },
                icon = scaleModeIcon(uiState.scaleMode),
                contentDescription = scaleDesc,
                size = targetSize,
                checked = uiState.scaleMode == VideoScaleMode.CROP_PAN
            )
        }

        PlayerCommandButton(
            onClick = onOpenMenu,
            icon = Icons.Default.MoreVert,
            contentDescription = menuDesc,
            size = targetSize
        )
    }

    if (uiState.hasSet && !uiState.isStream) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = uiState.positionText,
            style = MaterialTheme.typography.caption3,
            color = Color.Gray
        )
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
    // S2550: only the serving entry produces this, and this screen never calls it. Named rather than
    // folded into an `else` so the next reason added still has to be answered here on purpose.
    StreamChannelReason.NOT_ON_WIFI -> R.string.wear_stream_channel_offline
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
