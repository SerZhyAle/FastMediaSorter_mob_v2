package com.sza.fastmediasorter.wear.ui.player.audio

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WaveParticleBackground
import com.sza.fastmediasorter.wear.ui.player.common.PlayerScaffold
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSteps
import timber.log.Timber

/** Keeps white text readable over the animation, which is authored dark and never surprises us. */
private const val ANIMATION_SCRIM_ALPHA = 0.55f

/** A cover is someone else's artwork and may be white, so it is dimmed harder than the animation. */
private const val COVER_SCRIM_ALPHA = 0.7f

/**
 * Wear OS asks for a 48.dp press target and these buttons were drawn at 36.dp, which is below it.
 * Wear Compose 1.2.1 has no way to enlarge a press target without enlarging the button - the
 * modifier that does it on the phone lives in a Material library this module deliberately does not
 * depend on - so the buttons grow instead, and the row keeps its width by spacing them tighter.
 */
private val CONTROL_BUTTON_SIZE = 48.dp
private val CONTROL_ROW_SPACING = 4.dp

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

    KeepScreenOn(enabled = uiState.isDimmed)

    PlayerScaffold(
        // Both the clock and the scroll indicator are drawn by the scaffold above the content, so an
        // overlay alone cannot hide them - they have to be withheld, or the dark screen keeps two lit
        // elements on it.
        positionIndicator = if (uiState.isDimmed) null else { { PositionIndicator(listState) } },
        showTimeText = !uiState.isDimmed
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
                        // S1683: the bezel reaches the same two actions the buttons do, per strategic 6.2 -
                        // rotation moves inside the file and never changes the file.
                        if (step > 0) {
                            viewModel.seekForward()
                        } else {
                            viewModel.seekBackward()
                        }
                    },
                    actions = AudioPlayerActions(
                        onPlayPause = viewModel::togglePlayPause,
                        onSeekForward = viewModel::seekForward,
                        onSeekBackward = viewModel::seekBackward,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onSkipNext = viewModel::skipToNext,
                        onSkipPrevious = viewModel::skipToPrevious,
                        onToggleDimmed = viewModel::toggleDimmed
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

/**
 * Holds `FLAG_KEEP_SCREEN_ON` while [enabled] and clears it on every exit path, including leaving the
 * screen. A leaked flag keeps the watch display awake with nothing on it, which is the most expensive
 * mistake this file could make.
 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val window = LocalContext.current.findActivity()?.window
    DisposableEffect(window, enabled) {
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class AudioPlayerActions(
    val onPlayPause: () -> Unit,
    val onSeekForward: () -> Unit,
    val onSeekBackward: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onSkipNext: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val onToggleDimmed: () -> Unit
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
                onPlayPause = actions.onPlayPause,
                onSeekForward = actions.onSeekForward,
                onSeekBackward = actions.onSeekBackward,
                onToggleFavorite = actions.onToggleFavorite
            )
        }
        if (uiState.hasSet) {
            item {
                PagingControls(
                    positionText = uiState.positionText,
                    onSkipNext = actions.onSkipNext,
                    onSkipPrevious = actions.onSkipPrevious
                )
            }
        }
        item {
            ScreenOffControl(onToggleDimmed = actions.onToggleDimmed)
        }
    }
}

/**
 * S1683: the screen-off button gets a row of its own. The placement contract says "the same row", but
 * that was written when the transport and paging buttons were one row; four buttons already span
 * 180.dp of a 226.dp screen, and a fifth reaches 224.dp measured across the diameter, while the chord
 * at the height of the row is shorter still. Owner ruling 2026-08-16, asked with the measurement in
 * hand: a separate row, rather than a clipped one or a touch target below what Wear OS asks for.
 */
@Composable
private fun ScreenOffControl(onToggleDimmed: () -> Unit) {
    val screenOffDesc = stringResource(R.string.wear_screen_off)

    Button(
        onClick = onToggleDimmed,
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
    val backwardDesc = stringResource(R.string.wear_seek_backward)
    val forwardDesc = stringResource(R.string.wear_seek_forward)
    // The description names the action the press performs, and it changes with the state, so a screen
    // reader announces playing versus paused - the icon alone states it only to someone looking.
    val playPauseDesc = stringResource(if (isPlaying) R.string.pause else R.string.play)

    Row(
        horizontalArrangement = Arrangement.spacedBy(CONTROL_ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onSeekBackward,
            modifier = Modifier.size(CONTROL_BUTTON_SIZE),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text(
                text = "⏪",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.semantics { contentDescription = backwardDesc }
            )
        }

        Button(
            onClick = onPlayPause,
            modifier = Modifier.size(CONTROL_BUTTON_SIZE),
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Text(
                text = if (isPlaying) "⏸" else "▶️",
                style = MaterialTheme.typography.title2,
                modifier = Modifier.semantics { contentDescription = playPauseDesc }
            )
        }

        Button(
            onClick = onSeekForward,
            modifier = Modifier.size(CONTROL_BUTTON_SIZE),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text(
                text = "⏩",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.semantics { contentDescription = forwardDesc }
            )
        }

        Button(
            onClick = onToggleFavorite,
            modifier = Modifier.size(CONTROL_BUTTON_SIZE),
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

/**
 * S1683: paging keeps its own row instead of flanking the transport controls. Six buttons plus their
 * spacing need about 268.dp, and the watch this ticket was reported on is 226.dp wide, so a single row
 * would push the outer buttons off the round screen - the very defect being fixed here. The position
 * marker sits between the two buttons that move it, so a wrapped set never loses its landmark.
 */
@Composable
private fun PagingControls(
    positionText: String,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val previousDesc = stringResource(R.string.wear_previous_file)
    val nextDesc = stringResource(R.string.wear_next_file)

    Row(
        horizontalArrangement = Arrangement.spacedBy(CONTROL_ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onSkipPrevious,
            modifier = Modifier.size(CONTROL_BUTTON_SIZE),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text(
                text = "⏮",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.semantics { contentDescription = previousDesc }
            )
        }

        Text(
            text = positionText,
            style = MaterialTheme.typography.caption3,
            color = Color.Gray
        )

        Button(
            onClick = onSkipNext,
            modifier = Modifier.size(CONTROL_BUTTON_SIZE),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text(
                text = "⏭",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.semantics { contentDescription = nextDesc }
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
