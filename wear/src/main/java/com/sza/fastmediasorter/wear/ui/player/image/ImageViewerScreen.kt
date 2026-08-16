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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import timber.log.Timber
import kotlin.math.abs

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
                    onSwipeLeft = viewModel::navigateToNext,
                    onSwipeRight = viewModel::navigateToPrevious,
                    onToggleSlideshow = viewModel::toggleSlideshow,
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }
        }
    }
}

@Composable
private fun ImageViewerContent(
    uiState: ImageViewerUiState,
    isFavorite: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onToggleFavorite: () -> Unit
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
                    Timber.d(
                        "S1705: image drag start x=${down.position.x} band=$startedInDismissBand " +
                            "leaveToPlatform=$leaveToPlatform"
                    )
                    if (slop != null && !leaveToPlatform) {
                        horizontalDrag(slop.id) { change ->
                            dragOffset += change.positionChange().x
                            change.consume()
                        }
                        when {
                            dragOffset < -SWIPE_THRESHOLD -> onSwipeLeft()
                            dragOffset > SWIPE_THRESHOLD -> onSwipeRight()
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
            contentScale = ContentScale.Fit,
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
        
        val favoriteDesc = stringResource(R.string.wear_toggle_favorite)

        // Bottom info overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
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

            // Favorite toggle button
            Button(
                onClick = onToggleFavorite,
                // Wear OS asks for a 48.dp press target; this button was drawn at 32.dp, and this
                // module has no Material dependency offering a target larger than the button.
                modifier = Modifier
                    .size(48.dp)
                    .padding(top = 4.dp),
                colors = if (isFavorite) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors()
            ) {
                Text(
                    text = if (isFavorite) "❤️" else "🤍",
                    style = MaterialTheme.typography.caption3,
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
