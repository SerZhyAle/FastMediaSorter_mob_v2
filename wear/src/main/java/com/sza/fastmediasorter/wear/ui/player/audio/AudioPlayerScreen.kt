package com.sza.fastmediasorter.wear.ui.player.audio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.WaveParticleBackground
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSteps
import timber.log.Timber

/** Keeps white text readable over the animation, made 33% more visible per S1866. */
private const val ANIMATION_SCRIM_ALPHA = 0.37f

/** A cover artwork made 33% more visible per S1866, keeping text readable. */
private const val COVER_SCRIM_ALPHA = 0.47f

/**
 * Wear OS asks for a 48.dp press target and these buttons were drawn at 36.dp, which is below it.
 * Wear Compose 1.2.1 has no way to enlarge a press target without enlarging the button - the
 * modifier that does it on the phone lives in a Material library this module deliberately does not
 * depend on - so the buttons grow instead, and the row keeps its width by spacing them tighter.
 */
private val CONTROL_BUTTON_SIZE = 48.dp
private val CONTROL_ROW_SPACING = 4.dp

/** S1701: the glyph inside the 48.dp press target - the target is the reach, the icon is the read. */
private val CONTROL_ICON_SIZE = 24.dp

/**
 * S1701: the bar is drawn thin but grabbed over a taller strip - a 4.dp target on a watch is missed
 * far more often than it is hit, and the miss scrolls the list instead of seeking.
 */
private val PROGRESS_BAR_HEIGHT = 4.dp
private val PROGRESS_BAR_TOUCH_HEIGHT = 24.dp
private val PROGRESS_BAR_SPACING = 6.dp

/** A fully rounded cap on a bar this thin reads as a track rather than as a rectangle. */
private const val PROGRESS_BAR_CORNER_PERCENT = 50

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

    KeepScreenOnEffect(enabled = uiState.isPlaying || uiState.isDimmed)

    WearScreenScaffold(
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
                    listState = listState,
                    onRotaryStep = { step ->
                        // S1701 (ADR-1): the bezel now serves volume, the Wear OS media convention. It no
                        // longer seeks - phase 02 gave the screen a progress bar, which is a better way to
                        // reach a position than a bezel that also has to be a volume knob.
                        viewModel.onVolumeStep(step > 0)
                    },
                    actions = AudioPlayerActions(
                        onPlayPause = viewModel::togglePlayPause,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onSkipNext = viewModel::skipToNext,
                        onSkipPrevious = viewModel::skipToPrevious,
                        onToggleDimmed = viewModel::toggleDimmed,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onSeekTo = viewModel::seekTo
                    )
                )
            }
        }
        if (uiState.isDimmed) {
            DimOverlay(onExit = viewModel::toggleDimmed)
        }
    }
}

/**
 * S1683: an opaque black sheet over the whole player that any touch dismisses. It is deliberately not
 * a real display timeout: the screen pauses playback on ON_STOP by design (S0902), so letting the
 * watch sleep would stop the music this mode exists to keep playing. On an OLED watch the pixels
 * under an opaque black sheet are unlit anyway.
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
    val onPlayPause: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onSkipNext: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val onToggleDimmed: () -> Unit,
    val onToggleShuffle: () -> Unit,
    val onSeekTo: (Long) -> Unit
)

@Composable
private fun AudioPlayerContent(
    uiState: AudioPlayerUiState,
    isFavorite: Boolean,
    listState: ScalingLazyListState,
    onRotaryStep: (Int) -> Unit,
    actions: AudioPlayerActions
) {
    // S1683: this content is taller than a watch display, so it scrolls instead of being clipped -
    // the control row used to be pushed past the bottom edge, where it could not be reached at all.
    // The rotary binding sits on the same container deliberately: it consumes the event, so the bezel
    // cannot scroll this list and change the playback position at the same time. Touch still scrolls.
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .rotaryActionSteps(onRotaryStep),
        state = listState
    ) {
        item {
            TrackInfoSection(uiState = uiState)
        }
        // S1701: the volume readout, present only while the bezel is being turned and for a moment
        // after. It is its own item rather than an overlay so it cannot displace the control rows or
        // the progress bar, and nothing about it runs while it is hidden - strategic 3.2 protects the
        // budget the wave drawing already spends.
        if (uiState.isVolumeVisible) {
            item {
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
        }
        item {
            PlaybackTimeRow(
                currentPosition = uiState.currentPositionFormatted,
                duration = uiState.durationFormatted,
                progress = uiState.progress,
                durationMs = uiState.durationMs,
                onSeekTo = actions.onSeekTo
            )
        }
        item {
            PlaybackControls(
                isPlaying = uiState.isPlaying,
                isShuffleEnabled = uiState.isShuffleEnabled,
                onPlayPause = actions.onPlayPause,
                onSkipNext = actions.onSkipNext,
                onSkipPrevious = actions.onSkipPrevious,
                onToggleShuffle = actions.onToggleShuffle
            )
        }
        item {
            SecondaryControls(
                isFavorite = isFavorite,
                positionText = uiState.positionText,
                onToggleFavorite = actions.onToggleFavorite,
                onToggleDimmed = actions.onToggleDimmed
            )
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
            ?: uiState.mediaFile?.name
            ?: "Unknown"
        Text(
            text = title,
            style = MaterialTheme.typography.body1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        val fileName = uiState.mediaFile?.name
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
    isShuffleEnabled: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    val previousDesc = stringResource(R.string.wear_previous_file)
    val nextDesc = stringResource(R.string.wear_next_file)
    // The description names the action the press performs, and it changes with the state, so a screen
    // reader announces playing versus paused - the icon alone states it only to someone looking.
    val playPauseDesc = stringResource(if (isPlaying) R.string.pause else R.string.play)
    val shuffleDesc = stringResource(
        if (isShuffleEnabled) R.string.wear_shuffle_on else R.string.wear_shuffle_off
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(CONTROL_ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            onClick = onSkipPrevious,
            icon = Icons.Filled.SkipPrevious,
            description = previousDesc
        )

        ControlButton(
            onClick = onPlayPause,
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = playPauseDesc,
            highlighted = true
        )

        ControlButton(
            onClick = onSkipNext,
            icon = Icons.Filled.SkipNext,
            description = nextDesc
        )

        ControlButton(
            onClick = onToggleShuffle,
            icon = Icons.Filled.Shuffle,
            description = shuffleDesc,
            highlighted = isShuffleEnabled
        )
    }
}

/**
 * S1701: the second row the owner ruled on. It is no longer conditional on the set being known -
 * a favourite can be marked on a single file just as much as on one inside a browsed folder, and a
 * control that appears and disappears is the harder thing to learn. The set marker rides along here
 * because the buttons that move it now sit in the row above, and dropping it would leave a wrapping
 * folder without the landmark S1683 added it for.
 */
@Composable
private fun SecondaryControls(
    isFavorite: Boolean,
    positionText: String,
    onToggleFavorite: () -> Unit,
    onToggleDimmed: () -> Unit
) {
    val favoriteDesc = stringResource(R.string.wear_toggle_favorite)

    Row(
        horizontalArrangement = Arrangement.spacedBy(CONTROL_ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            onClick = onToggleFavorite,
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            description = favoriteDesc,
            highlighted = isFavorite
        )

        ScreenOffButton(onToggleDimmed = onToggleDimmed)

        Text(
            text = positionText,
            style = MaterialTheme.typography.caption3,
            color = Color.Gray
        )
    }
}

@Composable
private fun ScreenOffButton(onToggleDimmed: () -> Unit) {
    val screenOffDesc = stringResource(R.string.wear_screen_off)

    Button(
        onClick = {
            Timber.d("S1865: screen-off action tapped from secondary controls")
            onToggleDimmed()
        },
        modifier = Modifier.size(CONTROL_BUTTON_SIZE),
        colors = ButtonDefaults.secondaryButtonColors()
    ) {
        Text(
            text = "🌙",
            style = MaterialTheme.typography.body2,
            modifier = Modifier.semantics { contentDescription = screenOffDesc }
        )
    }
}

@Composable
private fun ControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    highlighted: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(CONTROL_BUTTON_SIZE),
        colors = if (highlighted) {
            ButtonDefaults.primaryButtonColors()
        } else {
            ButtonDefaults.secondaryButtonColors()
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(CONTROL_ICON_SIZE)
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
