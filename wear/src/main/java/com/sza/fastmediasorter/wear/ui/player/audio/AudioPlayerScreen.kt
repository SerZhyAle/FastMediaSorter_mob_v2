package com.sza.fastmediasorter.wear.ui.player.audio

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.DarkMode
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.StreamChannelReason
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackMode
import com.sza.fastmediasorter.wear.domain.model.displayName
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.WaveParticleBackground
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCommandButton
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCommandGrid
import com.sza.fastmediasorter.wear.ui.player.common.PlayerDialogVisibilities
import com.sza.fastmediasorter.wear.ui.player.common.PlayerDialogsHost
import com.sza.fastmediasorter.wear.ui.player.common.PlayerSeekActions
import com.sza.fastmediasorter.wear.ui.player.common.VolumeIndicatorSideBar
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSteps
import timber.log.Timber

/** Keeps white text readable over the animation, made 33% more visible per S1866. */
private const val ANIMATION_SCRIM_ALPHA = 0.37f

/** A cover artwork made 33% more visible per S1866, keeping text readable. */
private const val COVER_SCRIM_ALPHA = 0.47f

/** The error glyph is a mark, not a command, so it carries no press target. */
private val ERROR_GLYPH_SIZE = 48.dp

/**
 * S1701: the bar is drawn thin but grabbed over a taller strip - a 4.dp target on a watch is missed
 * far more often than it is hit, and the miss scrolls the list instead of seeking.
 */
private val PROGRESS_BAR_HEIGHT = 4.dp
private val PROGRESS_BAR_TOUCH_HEIGHT = 24.dp
private val PROGRESS_BAR_SPACING = 6.dp

/** A fully rounded cap on a bar this thin reads as a track rather than as a rectangle. */
private const val PROGRESS_BAR_CORNER_PERCENT = 50

private const val DRAG_THRESHOLD_UP_PX = -10f
private const val DRAG_THRESHOLD_DOWN_PX = 10f

/**
 * Audio player screen for Wear OS.
 * Shows album art, track info, progress, and playback controls.
 */
@Composable
fun AudioPlayerScreen(
    viewModel: AudioPlayerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val isPinned by viewModel.isPinned.collectAsState()
    // Hoisted out of the content so the scaffold drives its scroll indicator from the same state.
    val listState = rememberWearListState(initialCenterItemIndex = 0)

    // S0902: pause playback when the host activity stops (screen off / app backgrounded) -
    // onDispose only fires on navigation away, so without this the player kept running.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onHostStopped()
    }

    // S2166: attach to background session when the host activity returns to foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onHostStarted()
    }

    Timber.d("S2481: AudioPlayerScreen composed")

    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReceivers by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.closeScreen) {
        if (uiState.closeScreen) {
            onBack()
        }
    }

    KeepScreenOnEffect(enabled = uiState.isPlaying || uiState.isDimmed)

    val actions = rememberAudioPlayerActions(
        viewModel = viewModel,
        onBack = onBack,
        onShowActions = { showActions = true }
    )

    WearScreenScaffold(
        scrollState = listState,
        // Both the clock and the scroll indicator are drawn by the scaffold above the content, so an
        // overlay alone cannot hide them - they have to be withheld, or the dark screen keeps two lit
        // elements on it.
        positionIndicator = if (uiState.isDimmed) null else { { PositionIndicator(listState) } },
        showTimeText = !uiState.isDimmed,
        contentPadding = PaddingValues(0.dp)
    ) {
        PlayerBackground(
            albumArtUrl = uiState.albumArtUrl,
            isPlaying = uiState.isPlaying && !uiState.isDimmed
        )
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
                    isPinned = isPinned,
                    onRotaryStep = { step ->
                        // S1701 (ADR-1): the bezel now serves volume, the Wear OS media convention. It no
                        // longer seeks - phase 02 gave the screen a progress bar, which is a better way to
                        // reach a position than a bezel that also has to be a volume knob.
                        viewModel.onVolumeStep(step > 0)
                    },
                    actions = actions
                )
            }
        }
        if (uiState.isDimmed) {
            DimOverlay(onExit = viewModel::toggleDimmed)
        }
    }

    PlayerDialogsHost(
        operations = viewModel.fileOperations,
        visibilities = PlayerDialogVisibilities(
            showActions = showActions,
            showDeleteConfirm = showDeleteConfirm,
            showReceivers = showReceivers,
            onActionsVisibilityChange = { showActions = it },
            onDeleteVisibilityChange = { showDeleteConfirm = it },
            onReceiversVisibilityChange = { showReceivers = it }
        ),
        currentFileName = uiState.mediaFile?.name
    )
}

@Composable
private fun rememberAudioPlayerActions(
    viewModel: AudioPlayerViewModel,
    onBack: () -> Unit,
    onShowActions: () -> Unit
): AudioPlayerActions = remember(viewModel, onBack, onShowActions) {
    AudioPlayerActions(
        onBack = onBack,
        onPlayPause = viewModel::togglePlayPause,
        onToggleFavorite = viewModel::toggleFavorite,
        onTogglePin = viewModel::togglePin,
        onSkipNext = viewModel::skipToNext,
        onSkipPrevious = viewModel::skipToPrevious,
        onToggleDimmed = viewModel::toggleDimmed,
        onTogglePlaybackMode = viewModel::togglePlaybackMode,
        onFileOperations = onShowActions,
        seek = PlayerSeekActions(
            onSeekTo = viewModel::seekTo,
            onSeekBackward = viewModel::seekBackward,
            onSeekForward = viewModel::seekForward
        )
    )
}

/**
 * S1683: an opaque black sheet over the whole player that any touch dismisses. It is deliberately not
 * a real display timeout, and S2166 halved the reason. The reason still holds with the background
 * playback setting off: the screen pauses on ON_STOP (S0902), so letting the watch sleep would stop
 * the music this mode exists to keep playing. With the setting on and the track playing, the sleeping
 * watch keeps playing from the service, and what this sheet is left doing is keeping the screen
 * reachable in one touch rather than keeping the sound alive. On an OLED watch the pixels under an
 * opaque black sheet are unlit anyway, which is why the cheaper mode was never worth swapping in.
 */
@Composable
private fun DimOverlay(onExit: () -> Unit) {
    val exitDesc = stringResource(R.string.wear_screen_off_exit)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExit
            )
            .semantics { contentDescription = exitDesc }
    )
}

private data class AudioPlayerActions(
    val onBack: () -> Unit,
    val onPlayPause: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onTogglePin: () -> Unit,
    val onSkipNext: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val onToggleDimmed: () -> Unit,
    val onTogglePlaybackMode: () -> Unit,
    val onFileOperations: () -> Unit,
    val seek: PlayerSeekActions
)

@Composable
private fun AudioPlayerContent(
    uiState: AudioPlayerUiState,
    isFavorite: Boolean,
    isPinned: Boolean,
    onRotaryStep: (Int) -> Unit,
    actions: AudioPlayerActions
) {
    // S2477: The audio player elements are fitted onto a single screen without vertical list scrolling.
    // Vertical drag gesture / rotary wheel controls volume level.
    Timber.d("S2477: AudioPlayerContent single-screen layout composed")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .rotaryActionSteps(onRotaryStep)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount < DRAG_THRESHOLD_UP_PX) {
                        onRotaryStep(1)
                    } else if (dragAmount > DRAG_THRESHOLD_DOWN_PX) {
                        onRotaryStep(-1)
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(wearScreenInsets()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            TrackInfoSection(uiState = uiState)

            uiState.channelReason?.let { reason ->
                StreamChannelNotice(reason = reason)
            }

            PlaybackTimeRow(
                currentPosition = uiState.currentPositionFormatted,
                duration = uiState.durationFormatted,
                progress = uiState.progress,
                durationMs = uiState.durationMs,
                onSeekTo = actions.seek.onSeekTo
            )

            PlaybackControls(
                isPlaying = uiState.isPlaying,
                playbackMode = uiState.playbackMode,
                onPlayPause = actions.onPlayPause,
                onSkipNext = actions.onSkipNext,
                onSkipPrevious = actions.onSkipPrevious,
                onTogglePlaybackMode = actions.onTogglePlaybackMode,
                seek = actions.seek
            )

            SecondaryControls(
                isFavorite = isFavorite,
                isPinned = isPinned,
                isStream = uiState.isStream,
                actions = actions
            )

            if (!uiState.isStream && uiState.positionText.isNotEmpty()) {
                Text(
                    text = uiState.positionText,
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // S2477: Right side bar for volume overlay when volume is visible/changing
        if (uiState.isVolumeVisible) {
            VolumeIndicatorSideBar(level = uiState.volumeLevel, max = uiState.volumeMax)
        }
    }
}

@Composable
private fun TrackInfoSection(uiState: AudioPlayerUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val artist = uiState.artistName
            ?: uiState.mediaFile?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }
        if (!artist.isNullOrBlank()) {
            Text(
                text = artist,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val title = uiState.trackTitle
            ?: uiState.mediaFile?.title?.takeIf { it.isNotBlank() }
            ?: uiState.mediaFile?.displayName
            ?: "Unknown"
        Text(
            text = title,
            style = MaterialTheme.typography.body1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        val fileName = uiState.mediaFile?.displayName
        if (fileName != null && title != fileName) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.caption2,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StreamChannelNotice(reason: StreamChannelReason) {
    val messageRes = reason.toMessageRes() ?: return
    val message = stringResource(messageRes)
    Text(
        text = message,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
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

/**
 * S1683: the cover fills the screen behind the controls instead of sitting in a 64.dp circle among
 * them, and a file without one gets the brand animation rather than an emoji. Both are decorative -
 * every piece of information on this screen is stated in text above.
 */
@Composable
private fun PlayerBackground(
    albumArtUrl: String?,
    isPlaying: Boolean
) {
    // S2000: this screen used to sit on the navigation host's black fill, which the app-wide
    // background layer replaced. It paints its own now, so what follows is unchanged by whatever
    // the owner chose as the app background.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    )
    val painter = albumArtUrl?.let { rememberAsyncImagePainter(model = it) }
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
    // MediaStore hands out an album-art uri for every track that belongs to an album and promises
    // nothing about the album having a cover. Measured on the watch: a track without one left a black
    // screen, because a non-null uri had been read as "there is a cover". So the fallback is the uri
    // failing to produce an image, not the uri being absent.
    val coverShown = painter?.state is AsyncImagePainter.State.Success
    // S2000: this screen remains the sole drawer of the animation here - WearAppBackground draws it
    // behind every other screen and is covered by the opaque fill above, so it is never drawn twice.
    if (!coverShown) {
        WaveParticleBackground(
            modifier = Modifier.fillMaxSize(),
            running = isPlaying
        )
    }
    val scrimAlpha = if (coverShown) COVER_SCRIM_ALPHA else ANIMATION_SCRIM_ALPHA
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
    )
}

/**
 * S1701: position is shown by the same bar the user drags, so what is visible is what is grabbed.
 * The two times stay where they already were, at the ends of the row, and the bar takes the space
 * the 100.dp ring used to occupy in the centre of a round screen.
 */
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

/**
 * The drag consumes its own events so the scrolling column underneath cannot move at the same time -
 * the same claim the bezel binding already makes on this screen, for the same reason.
 */
@Composable
private fun RowScope.SeekBar(
    progress: Float,
    durationMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val seekDesc = stringResource(R.string.wear_seek_drag)
    // The seek target is a fraction of the bar, so the bar has to report how wide it ended up after
    // the two time labels took theirs.
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

/**
 * S1701: the four buttons the owner ruled on 2026-08-16 - previous, play and pause, next, shuffle.
 * Width recomputed for this composition rather than inherited from S1683: four 48.dp targets and
 * three 4.dp gaps span 204.dp of the 226.dp screen this ticket was reported on, leaving 11.dp either
 * side at the centre chord. Seeking left this row with the ring: it lives on the bar above.
 */
@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    playbackMode: WearPlaybackMode,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlaybackMode: () -> Unit,
    seek: PlayerSeekActions
) {
    Timber.d("S2529: AudioPlayerScreen PlaybackControls composed, isPlaying=$isPlaying")
    val previousDesc = stringResource(R.string.wear_previous_file)
    val nextDesc = stringResource(R.string.wear_next_file)
    val seekBackwardDesc = stringResource(R.string.wear_seek_backward)
    val seekForwardDesc = stringResource(R.string.wear_seek_forward)
    val playPauseDesc = stringResource(if (isPlaying) R.string.pause else R.string.play)
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

    PlayerCommandGrid { targetSize ->
        PlayerCommandButton(
            onClick = onSkipPrevious,
            icon = Icons.Filled.SkipPrevious,
            contentDescription = previousDesc,
            modifier = Modifier.size(targetSize),
            onLongClick = seek.onSeekBackward,
            onLongClickLabel = seekBackwardDesc
        )

        PlayerCommandButton(
            onClick = onPlayPause,
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = playPauseDesc,
            modifier = Modifier.size(targetSize),
            checked = true,
            iconTint = colorResource(ContentTypeCatalog.tintFor(WearContentType.MUSIC))
        )

        PlayerCommandButton(
            onClick = onTogglePlaybackMode,
            icon = playbackModeIcon,
            contentDescription = playbackModeDesc,
            modifier = Modifier.size(targetSize),
            checked = playbackMode != WearPlaybackMode.SEQUENTIAL
        )

        PlayerCommandButton(
            onClick = onSkipNext,
            icon = Icons.Filled.SkipNext,
            contentDescription = nextDesc,
            modifier = Modifier.size(targetSize),
            onLongClick = seek.onSeekForward,
            onLongClickLabel = seekForwardDesc
        )
    }
}

/**
 * S1701: the second row the owner ruled on. It is no longer conditional on the set being known -
 * a favourite can be marked on a single file just as much as on one inside a browsed folder, and a
 * control that appears and disappears is the harder thing to learn. The set marker rides along here
 * because the buttons that move it now sit in the row above, and dropping it would leave a wrapping
 * folder without the landmark S1683 added it for.
 *
 * S2472: the back command opens the row. This screen's controls are permanent list rows, so its back
 * button is permanent too - the affordance follows the window's logic, and this window never hides
 * its controls.
 */
@Composable
private fun SecondaryControls(
    isFavorite: Boolean,
    isPinned: Boolean,
    isStream: Boolean,
    actions: AudioPlayerActions
) {
    val favoriteDesc = stringResource(R.string.wear_toggle_favorite)
    val pinDesc = stringResource(
        if (isPinned) R.string.wear_player_stream_unpin else R.string.wear_player_stream_pin
    )
    val fileOpDesc = stringResource(R.string.wear_file_op_actions)

    PlayerCommandGrid { targetSize ->
        PlayerCommandButton(
            onClick = actions.onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.wear_navigate_back),
            modifier = Modifier.size(targetSize)
        )

        PlayerCommandButton(
            onClick = actions.onToggleFavorite,
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = favoriteDesc,
            modifier = Modifier.size(targetSize),
            checked = isFavorite
        )

        if (isStream) {
            PlayerCommandButton(
                onClick = actions.onTogglePin,
                icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = pinDesc,
                modifier = Modifier.size(targetSize),
                checked = isPinned
            )
        } else {
            PlayerCommandButton(
                onClick = actions.onFileOperations,
                icon = Icons.Default.MoreVert,
                contentDescription = fileOpDesc,
                modifier = Modifier.size(targetSize)
            )
        }

        ScreenOffButton(onToggleDimmed = actions.onToggleDimmed, modifier = Modifier.size(targetSize))
    }
}

@Composable
private fun ScreenOffButton(onToggleDimmed: () -> Unit, modifier: Modifier) {
    val screenOffDesc = stringResource(R.string.wear_screen_off)

    PlayerCommandButton(
        onClick = onToggleDimmed,
        icon = Icons.Filled.DarkMode,
        contentDescription = screenOffDesc,
        modifier = modifier
    )
}

@Composable
private fun ErrorContent(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = message,
            tint = MaterialTheme.colors.error,
            modifier = Modifier.size(ERROR_GLYPH_SIZE)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}
