package com.sza.fastmediasorter.wear.ui.player.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCommandButton
import timber.log.Timber

/** The scrim behind the panel, dark enough to read white text over any picture. */
private const val OVERLAY_SCRIM_ALPHA = 0.6f

private val FAVORITE_ROW_TOP_PADDING = 4.dp
private val ERROR_GLYPH_SIZE = 48.dp

private const val SWIPE_THRESHOLD = 100f

// Measured on the owner's Galaxy Watch 7, 480 px wide (S1683 research 02): the platform
// swipe-to-dismiss needs about 305 px of rightward travel, so only a drag starting left of this
// fraction of the width can ever reach it. Leaving that band's rightward drags unconsumed is what
// gives this screen back an exit gesture; forward paging never competes, since dismiss ignores
// leftward travel entirely.
private const val DISMISS_BAND_FRACTION = 0.36f

/**
 * Image viewer screen for Wear OS.
 * Displays images with swipe navigation between photos.
 */
@Composable
fun ImageViewerScreen(
    viewModel: ImageViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    Timber.d("ImageViewerScreen composing, index: ${uiState.currentIndex}")

    // An image has no playing state, so having one on screen is itself the active condition.
    KeepScreenOnEffect(enabled = uiState.mediaFile != null)

    WearScreenScaffold(contentPadding = PaddingValues(0.dp)) {
        when {
            uiState.error != null -> {
                ErrorContent(message = uiState.error!!)
            }
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            uiState.mediaFile != null -> {
                ImageViewerContent(
                    uiState = uiState,
                    isFavorite = isFavorite,
                    actions = ImageViewerActions(
                        onSwipeLeft = viewModel::navigateToNext,
                        onSwipeRight = viewModel::navigateToPrevious,
                        onToggleSlideshow = viewModel::toggleSlideshow,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onToggleScaleMode = viewModel::toggleScaleMode,
                        onScreenTap = viewModel::onScreenTap
                    )
                )
            }
        }
    }
}

/** The image viewer's callbacks, bundled the way the audio and video players already bundle theirs. */
private data class ImageViewerActions(
    val onSwipeLeft: () -> Unit,
    val onSwipeRight: () -> Unit,
    val onToggleSlideshow: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onToggleShuffle: () -> Unit,
    val onToggleScaleMode: () -> Unit,
    val onScreenTap: () -> Unit
)

@Composable
private fun ImageViewerContent(
    uiState: ImageViewerUiState,
    isFavorite: Boolean,
    actions: ImageViewerActions
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val dismissBandPx = size.width * DISMISS_BAND_FRACTION
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedInDismissBand = down.position.x <= dismissBandPx
                    var dragOffset = 0f
                    var leaveToPlatform = false
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        if (startedInDismissBand && overSlop > 0f) {
                            leaveToPlatform = true
                        } else {
                            change.consume()
                            dragOffset += overSlop
                        }
                    }
                    // A lift that never crossed the horizontal slop is a tap, not a page turn.
                    if (slop == null && !leaveToPlatform) {
                        actions.onScreenTap()
                    }
                    if (slop != null && !leaveToPlatform) {
                        horizontalDrag(slop.id) { change ->
                            dragOffset += change.positionChange().x
                            change.consume()
                        }
                        when {
                            dragOffset < -SWIPE_THRESHOLD -> actions.onSwipeLeft()
                            dragOffset > SWIPE_THRESHOLD -> actions.onSwipeRight()
                        }
                    }
                }
            }
    ) {
        // Image
        var isImageLoading by remember { mutableFloatStateOf(1f) }

        AsyncImage(
            model = uiState.mediaFile?.uri,
            contentDescription = uiState.mediaFile?.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = if (uiState.scaleMode == VideoScaleMode.CROP_PAN) {
                ContentScale.Crop
            } else {
                ContentScale.Fit
            },
            onState = { state ->
                isImageLoading = when (state) {
                    is AsyncImagePainter.State.Loading -> 1f
                    else -> 0f
                }
            }
        )

        // Loading indicator while image loads
        if (isImageLoading > 0f) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
            )
        }

        if (uiState.showControls) {
            ImageBottomPanel(
                uiState = uiState,
                isFavorite = isFavorite,
                onToggleFavorite = actions.onToggleFavorite,
                onToggleSlideshow = actions.onToggleSlideshow,
                onToggleShuffle = actions.onToggleShuffle,
                onToggleScaleMode = actions.onToggleScaleMode,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * The bottom panel of the image viewer: name, position, slideshow state and the commands.
 *
 * Extracted from the content composable in S2006 - once the panel became conditional the caller
 * crossed detekt's LongMethod ceiling, and a panel that can be hidden is its own thing anyway.
 */
@Composable
private fun ImageBottomPanel(
    uiState: ImageViewerUiState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleScaleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val favoriteDesc = stringResource(R.string.wear_toggle_favorite)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = OVERLAY_SCRIM_ALPHA))
            // The scrim stays full-bleed; only its content is inset, so the labels at the ends
            // of the row keep clear of the curve at the bottom of a round screen.
            .padding(wearScreenInsets()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // File name
        Text(
            text = uiState.mediaFile?.name ?: "",
            style = MaterialTheme.typography.caption2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White
        )

        // Navigation info
        if (uiState.totalCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // The arrows stay outside the resource: they are decoration, and a translator
                // handed "← Prev" has to guess whether the glyph is part of the word.
                Text(
                    text = "← " + stringResource(R.string.previous),
                    style = MaterialTheme.typography.caption3,
                    color = Color.Gray
                )
                Text(
                    text = uiState.positionText,
                    style = MaterialTheme.typography.caption3,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.next) + " →",
                    style = MaterialTheme.typography.caption3,
                    color = Color.Gray
                )
            }
        }

        // Slideshow indicator
        if (uiState.isSlideshowActive) {
            Text(
                text = "▶ " + stringResource(R.string.slideshow_active),
                style = MaterialTheme.typography.caption3,
                color = Color.Green,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        ImageCommandRow(
            uiState = uiState,
            onToggleSlideshow = onToggleSlideshow,
            onToggleShuffle = onToggleShuffle,
            onToggleScaleMode = onToggleScaleMode
        )

        PlayerCommandButton(
            onClick = onToggleFavorite,
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = favoriteDesc,
            modifier = Modifier.padding(top = FAVORITE_ROW_TOP_PADDING),
            checked = isFavorite
        )
    }
}

/** The image viewer's own command row: slideshow, traversal order and fit, in that order. */
@Composable
private fun ImageCommandRow(
    uiState: ImageViewerUiState,
    onToggleSlideshow: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleScaleMode: () -> Unit
) {
    val slideshowDesc = stringResource(
        if (uiState.isSlideshowActive) R.string.wear_slideshow_stop else R.string.wear_slideshow_start
    )
    val shuffleDesc = stringResource(
        if (uiState.isShuffleEnabled) R.string.wear_shuffle_on else R.string.wear_shuffle_off
    )
    // The same two glyphs the video player uses for the same choice, mapped the same way round.
    val scaleIcon = if (uiState.scaleMode == VideoScaleMode.CROP_PAN) {
        Icons.Filled.AspectRatio
    } else {
        Icons.Filled.CropFree
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerCommandButton(
            onClick = onToggleSlideshow,
            icon = if (uiState.isSlideshowActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = slideshowDesc,
            checked = uiState.isSlideshowActive
        )

        PlayerCommandButton(
            onClick = onToggleShuffle,
            // Shape as well as tint - the accessibility constraint asks for two signals.
            icon = if (uiState.isShuffleEnabled) {
                Icons.Filled.Shuffle
            } else {
                Icons.AutoMirrored.Filled.ArrowForward
            },
            contentDescription = shuffleDesc,
            checked = uiState.isShuffleEnabled
        )

        PlayerCommandButton(
            onClick = onToggleScaleMode,
            icon = scaleIcon,
            contentDescription = stringResource(R.string.wear_scale_mode),
            checked = uiState.scaleMode == VideoScaleMode.CROP_PAN
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
