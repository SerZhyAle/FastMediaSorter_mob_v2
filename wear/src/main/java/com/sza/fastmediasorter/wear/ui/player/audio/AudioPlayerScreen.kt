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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import com.sza.fastmediasorter.wear.ui.common.AnimationIntent
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.WEAR_LIST_NO_ANCHOR
import com.sza.fastmediasorter.wear.ui.common.WaveParticleBackground
import com.sza.fastmediasorter.wear.ui.common.WearAction
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.common.wearBandEdgeOffset
import com.sza.fastmediasorter.wear.ui.common.wearChordInset
import com.sza.fastmediasorter.wear.ui.common.wearIsCompactScreen
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.player.common.PRIMARY_ROW_COLUMNS
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCommandButton
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCommandGrid
import com.sza.fastmediasorter.wear.ui.player.common.PlayerDialogVisibilities
import com.sza.fastmediasorter.wear.ui.player.common.PlayerDialogsHost
import com.sza.fastmediasorter.wear.ui.player.common.PlayerOverflowMenu
import com.sza.fastmediasorter.wear.ui.player.common.PlayerProgressRing
import com.sza.fastmediasorter.wear.ui.player.common.PlayerSeekActions
import com.sza.fastmediasorter.wear.ui.player.common.VolumeIndicatorSideBar
import com.sza.fastmediasorter.wear.ui.player.common.playerCommandBandWidth
import com.sza.fastmediasorter.wear.ui.player.common.playerMenuAction
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSteps
import com.sza.fastmediasorter.wear.ui.player.common.secondaryRowColumns
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
 * Floor under the track name's own inset (S2273). It is what the section paid before the chord was
 * measured, and it is the whole answer on a square screen, where the chord never narrows.
 */
private val TRACK_INFO_MIN_PADDING = 8.dp

/** Keeps text ink and rounded layout coordinates from landing exactly on a round-glass chord. */
private val TRACK_INFO_EDGE_SAFETY = 1.dp

@Composable
private fun trackInfoPadding(topInset: Dp, sideInset: Dp): Dp = (
    wearChordInset(topInset) - sideInset + TRACK_INFO_EDGE_SAFETY
    ).coerceAtLeast(TRACK_INFO_MIN_PADDING)

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
    val listState = rememberWearListState(initialCenterItemIndex = WEAR_LIST_NO_ANCHOR)

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
    Timber.d("S2769: Audio track title chord safety margin active")
    var showMenu by rememberSaveable { mutableStateOf(false) }
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
        val screenInsets = wearScreenInsets()
        // S2273: the track name is the first child of a SpaceEvenly column, so its worst edge is the
        // column's own top - about a tenth of the way down, where the chord is far shorter than the
        // diameter and the proportional inset alone left the name sliced flat against the arc on both
        // sides. Text is the one element here that can pay for the chord out of its own width: it
        // wraps and ellipsizes, and narrower and whole beats wide and cut. The chord is measured from glass.
        val screenSideInset = screenInsets.calculateLeftPadding(LayoutDirection.Ltr)
        val trackInfoPadding = trackInfoPadding(screenInsets.calculateTopPadding(), screenSideInset)
        // S2273: measured at 192 dp, where the bottom command row failed `clip-check` at 206.8 px
        // against a 192 px limit. That row is four touch targets and cannot pay a chord inset out of
        // its own width past the glyphs, so it does both: it stands where the glass is at least
        // [playerCommandBandWidth] wide, and it takes exactly that width there. The lift comes out of
        // the column's SpaceEvenly gaps, which is why nothing else on the screen moves; the row is the
        // last child, so the column's own bottom is what places it.
        // S2766: the lift is charged for the row actually drawn. A two-command row is narrower, so it
        // stands lower and hands the difference back to the column, which is where the compact
        // composition finds part of the height its children were short of.
        val commandRowBottom = wearBandEdgeOffset(playerCommandBandWidth(secondaryRowColumns()))
            .coerceAtLeast(screenInsets.calculateBottomPadding())
        val commandRowPadding = (wearChordInset(commandRowBottom) - screenSideInset)
            .coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = screenSideInset,
                    top = screenInsets.calculateTopPadding(),
                    end = screenSideInset,
                    bottom = commandRowBottom
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            PlayerColumnContent(
                uiState = uiState,
                isFavorite = isFavorite,
                actions = actions,
                paddings = PlayerColumnPaddings(trackInfoPadding, commandRowPadding),
                onOpenMenu = { showMenu = true }
            )
        }

        // S2477: Right side bar for volume overlay when volume is visible/changing
        if (uiState.isVolumeVisible) {
            VolumeIndicatorSideBar(level = uiState.volumeLevel, max = uiState.volumeMax)
        }
    }

    if (showMenu) {
        PlayerOverflowMenu(
            actions = playerMenuActions(
                uiState = uiState,
                isFavorite = isFavorite,
                isPinned = isPinned,
                actions = actions,
                onDismiss = { showMenu = false }
            ),
            onDismiss = { showMenu = false }
        )
    }
}

/** The two clearances the column's children need, each measured against the round glass. */
private data class PlayerColumnPaddings(
    val trackInfo: Dp,
    val commandRow: Dp
)

/**
 * The children of the player column, in the order the glass shows them.
 *
 * S2766: which children there are depends on the screen size class, which is the whole reason this
 * ticket exists - a proportion can shrink a child but cannot remove one, and at 192 dp one had to go.
 */
@Composable
private fun ColumnScope.PlayerColumnContent(
    uiState: AudioPlayerUiState,
    isFavorite: Boolean,
    actions: AudioPlayerActions,
    paddings: PlayerColumnPaddings,
    onOpenMenu: () -> Unit
) {
    TrackInfoSection(uiState = uiState, horizontalPadding = paddings.trackInfo)

    uiState.channelReason?.let { reason ->
        StreamChannelNotice(reason = reason)
    }

    // S2766: the time row is what the compact column cannot afford - its children ask about 23.5 dp
    // more than the glass leaves them at 192 dp, and this row costs about 24. Below the breakpoint
    // the position moves onto the ring around the play button instead.
    if (!wearIsCompactScreen()) {
        PlaybackTimeRow(
            currentPosition = uiState.currentPositionFormatted,
            duration = uiState.durationFormatted,
            progress = uiState.progress,
            durationMs = uiState.durationMs,
            onSeekTo = actions.seek.onSeekTo
        )
    }

    PlaybackControls(
        isPlaying = uiState.isPlaying,
        progress = uiState.progress,
        onPlayPause = actions.onPlayPause,
        onSkipNext = actions.onSkipNext,
        onSkipPrevious = actions.onSkipPrevious,
        seek = actions.seek
    )

    SecondaryControls(
        isFavorite = isFavorite,
        actions = actions,
        horizontalPadding = paddings.commandRow,
        onOpenMenu = onOpenMenu
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

/**
 * Everything the two command rows shed, in one list.
 *
 * S2766: the rows carry three primary and two or three secondary commands now, so the commands they
 * no longer show need a container that is itself reachable - otherwise they have not moved, they have
 * disappeared. The favourite appears here only below the breakpoint, because above it the secondary
 * row still has the slot.
 *
 * The file operations stay their own entry rather than being merged in: that dialog answers what the
 * file capability policy allows (ADR-4), and a playback mode inside it would make that answer wrong.
 */
@Composable
private fun playerMenuActions(
    uiState: AudioPlayerUiState,
    isFavorite: Boolean,
    isPinned: Boolean,
    actions: AudioPlayerActions,
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
    val favoriteLabel = stringResource(R.string.wear_toggle_favorite)
    val pinLabel = stringResource(
        if (isPinned) R.string.wear_player_stream_unpin else R.string.wear_player_stream_pin
    )
    val screenOffLabel = stringResource(R.string.wear_screen_off)
    val fileActionsLabel = stringResource(R.string.wear_player_file_actions)
    val showFavorite = wearIsCompactScreen()

    return buildList {
        add(
            playerMenuAction(
                playbackModeLabel,
                playbackModeIcon,
                onDismiss,
                actions.onTogglePlaybackMode
            )
        )
        if (showFavorite) {
            val icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
            add(playerMenuAction(favoriteLabel, icon, onDismiss, actions.onToggleFavorite))
        }
        if (uiState.isStream) {
            val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
            add(playerMenuAction(pinLabel, icon, onDismiss, actions.onTogglePin))
        }
        add(
            playerMenuAction(
                screenOffLabel,
                Icons.Filled.DarkMode,
                onDismiss,
                actions.onToggleDimmed
            )
        )
        if (!uiState.isStream) {
            add(
                playerMenuAction(
                    fileActionsLabel,
                    Icons.AutoMirrored.Filled.List,
                    onDismiss,
                    actions.onFileOperations
                )
            )
        }
    }
}

@Composable
private fun TrackInfoSection(uiState: AudioPlayerUiState, horizontalPadding: Dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
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
    // S2550: only the serving entry produces this, and this screen never calls it. Named rather than
    // folded into an `else` so the next reason added still has to be answered here on purpose.
    StreamChannelReason.NOT_ON_WIFI -> R.string.wear_stream_channel_offline
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
            running = isPlaying,
            intent = AnimationIntent.AMBIENT
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
 * S1701 gave this row the four buttons the owner ruled on 2026-08-16 - previous, play and pause,
 * next, shuffle.
 *
 * S2766 takes it back to three. Google describes the primary media row as previous, play/pause and
 * next, and the playback mode is not one of them - it moves to the player menu. The arithmetic says
 * the same thing: four 48 dp cells with their gaps ask for 204 dp of row and the small round glass is
 * 192 dp across, while three ask for 152 dp of a 153.6 dp content box.
 *
 * Below the breakpoint the position rides a ring around the play button, because the compact
 * composition has no separate time row for it to live on.
 */
@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    progress: Float,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    seek: PlayerSeekActions
) {
    Timber.d("S2529: AudioPlayerScreen PlaybackControls composed, isPlaying=$isPlaying")
    val previousDesc = stringResource(R.string.wear_previous_file)
    val nextDesc = stringResource(R.string.wear_next_file)
    val seekBackwardDesc = stringResource(R.string.wear_seek_backward)
    val seekForwardDesc = stringResource(R.string.wear_seek_forward)
    val playPauseDesc = stringResource(if (isPlaying) R.string.pause else R.string.play)
    val ringed = wearIsCompactScreen()

    PlayerCommandGrid(columns = PRIMARY_ROW_COLUMNS) { targetSize ->
        PlayerCommandButton(
            onClick = onSkipPrevious,
            icon = Icons.Filled.SkipPrevious,
            contentDescription = previousDesc,
            size = targetSize,
            onLongClick = seek.onSeekBackward,
            onLongClickLabel = seekBackwardDesc
        )

        PlayPauseCommand(
            isPlaying = isPlaying,
            contentDescription = playPauseDesc,
            size = targetSize,
            progress = progress.takeIf { ringed },
            onPlayPause = onPlayPause
        )

        PlayerCommandButton(
            onClick = onSkipNext,
            icon = Icons.Filled.SkipNext,
            contentDescription = nextDesc,
            size = targetSize,
            onLongClick = seek.onSeekForward,
            onLongClickLabel = seekForwardDesc
        )
    }
}

/** The play button, wrapped in the position ring when [progress] is given and bare when it is not. */
@Composable
private fun PlayPauseCommand(
    isPlaying: Boolean,
    contentDescription: String,
    size: Dp,
    progress: Float?,
    onPlayPause: () -> Unit
) {
    val button: @Composable () -> Unit = {
        PlayerCommandButton(
            onClick = onPlayPause,
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = contentDescription,
            size = size,
            checked = true,
            iconTint = colorResource(ContentTypeCatalog.tintFor(WearContentType.MUSIC))
        )
    }
    if (progress == null) {
        button()
    } else {
        PlayerProgressRing(progress = progress, content = button)
    }
}

/**
 * S1701: the second row the owner ruled on. It is no longer conditional on the set being known -
 * a favourite can be marked on a single file just as much as on one inside a browsed folder, and a
 * control that appears and disappears is the harder thing to learn.
 *
 * S2472: the back command opens the row. This screen's controls are permanent list rows, so its back
 * button is permanent too - the affordance follows the window's logic, and this window never hides
 * its controls.
 *
 * S2766 narrows the row rather than cancelling either ruling: Google caps a secondary media row at
 * two commands below the breakpoint and three above it. Back stays because S2472 made it permanent,
 * "more" stays because without it everything this row shed is unreachable, and the favourite is the
 * one that fits the third slot on the larger glass. Everything else opens from the menu.
 */
@Composable
private fun SecondaryControls(
    isFavorite: Boolean,
    actions: AudioPlayerActions,
    horizontalPadding: Dp,
    onOpenMenu: () -> Unit
) {
    val favoriteDesc = stringResource(R.string.wear_toggle_favorite)
    val menuDesc = stringResource(R.string.wear_file_op_actions)

    PlayerCommandGrid(
        horizontalPadding = horizontalPadding,
        columns = secondaryRowColumns()
    ) { targetSize ->
        PlayerCommandButton(
            onClick = actions.onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.wear_navigate_back),
            size = targetSize
        )

        if (!wearIsCompactScreen()) {
            PlayerCommandButton(
                onClick = actions.onToggleFavorite,
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = favoriteDesc,
                size = targetSize,
                checked = isFavorite
            )
        }

        PlayerCommandButton(
            onClick = onOpenMenu,
            icon = Icons.Default.MoreVert,
            contentDescription = menuDesc,
            size = targetSize
        )
    }
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
