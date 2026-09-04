package com.sza.fastmediasorter.wear.ui.player.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
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
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackMode
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.domain.model.displayName
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordance
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordanceRole
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCommandButton
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCommandGrid
import timber.log.Timber

/** The scrim behind the panel, dark enough to read white text over any picture. */
private const val OVERLAY_SCRIM_ALPHA = 0.6f

private val ERROR_GLYPH_SIZE = 48.dp

private const val SWIPE_THRESHOLD = 100f

// Measured on the owner's Galaxy Watch 7, 480 px wide (S1683 research 02): the platform
// swipe-to-dismiss needs about 305 px of rightward travel, so only a drag starting left of this
// fraction of the width can ever reach it. Leaving that band's rightward drags unconsumed is what
// gives this screen back an exit gesture; forward paging never competes, since dismiss ignores
// leftward travel entirely.
private const val DISMISS_BAND_FRACTION = 0.36f

// S2480, the owner's zones: a tap in the outer thirds pages, a tap in the middle band shows or
// hides the panel. Paging by tap is what makes the swipe optional - on a 480 px round screen the
// swipe competes with the platform dismiss gesture, so it cannot be the only way to page.
private const val TAP_ZONE_PREVIOUS_END_FRACTION = 0.3f
private const val TAP_ZONE_NEXT_START_FRACTION = 0.7f

/** Neutral zoom, and also the floor: a cropped picture never shrinks inside its frame. */
private const val IMAGE_ZOOM_MIN = 1f
private const val IMAGE_ZOOM_MAX = 4f

/**
 * Image viewer screen for Wear OS.
 * Displays images with swipe navigation between photos.
 */
@Composable
fun ImageViewerScreen(
    viewModel: ImageViewerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReceivers by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.closeScreen) {
        if (uiState.closeScreen) {
            onBack()
        }
    }

    // An image has no playing state, so having one on screen is itself the active condition.
    KeepScreenOnEffect(enabled = uiState.mediaFile != null)

    WearScreenScaffold(
        showTimeText = uiState.showControls,
        contentPadding = PaddingValues(0.dp)
    ) {
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
                        onBack = onBack,
                        onSwipeLeft = viewModel::navigateToNext,
                        onSwipeRight = viewModel::navigateToPrevious,
                        onToggleSlideshow = viewModel::toggleSlideshow,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onTogglePlaybackMode = viewModel::togglePlaybackMode,
                        onToggleScaleMode = viewModel::toggleScaleMode,
                        onScreenTap = viewModel::onScreenTap,
                        onFileOperations = { showActions = true }
                    )
                )
            }
        }
    }

    com.sza.fastmediasorter.wear.ui.player.common.PlayerDialogsHost(
        operations = viewModel.fileOperations,
        visibilities = com.sza.fastmediasorter.wear.ui.player.common.PlayerDialogVisibilities(
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

/** The image viewer's callbacks, bundled the way the audio and video players already bundle theirs. */
private data class ImageViewerActions(
    val onBack: () -> Unit,
    val onSwipeLeft: () -> Unit,
    val onSwipeRight: () -> Unit,
    val onToggleSlideshow: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onTogglePlaybackMode: () -> Unit,
    val onToggleScaleMode: () -> Unit,
    val onScreenTap: () -> Unit,
    val onFileOperations: () -> Unit
)

/**
 * Sends a tap to one of three outcomes by where it landed across the width.
 *
 * `onSwipeRight` is this screen's name for "previous" and `onSwipeLeft` for "next", kept from when
 * the swipe was the only way to page. A zone tap deliberately never reveals the panel: the user who
 * taps the edge asked for the next picture, not for a row of buttons over it.
 */
private fun dispatchZoneTap(xFraction: Float, actions: ImageViewerActions) {
    Timber.d("S2480: zone tap at fraction $xFraction")
    when {
        xFraction <= TAP_ZONE_PREVIOUS_END_FRACTION -> actions.onSwipeRight()
        xFraction >= TAP_ZONE_NEXT_START_FRACTION -> actions.onSwipeLeft()
        else -> actions.onScreenTap()
    }
}

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
                    // A lift that never crossed the horizontal slop is a tap, not a page turn - but
                    // only when no panel button took it first. S2480, measured on the watch: the
                    // Play button sits in the middle band, so its press reached this node as well
                    // and turned the panel straight back on, undoing the hide the command had just
                    // performed. A button consumes the down in the main pass, which travels child
                    // to parent, so the consumption is already visible here.
                    if (slop == null && !leaveToPlatform && !down.isConsumed) {
                        dispatchZoneTap(down.position.x / size.width.toFloat(), actions)
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
        var isImageLoading by remember { mutableStateOf(true) }

        ZoomableImage(
            uiState = uiState,
            onLoadingChange = { isImageLoading = it }
        )

        // Loading indicator while image loads
        if (isImageLoading) {
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
                actions = actions,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * The picture itself, pinchable and pannable while it is cropped.
 *
 * S2480: zoom and offset are remembered against the file and the mode, so both reset when either
 * changes - an enlargement is a look at the current frame, never a viewing mode the next picture
 * inherits.
 */
@Composable
private fun ZoomableImage(
    uiState: ImageViewerUiState,
    onLoadingChange: (Boolean) -> Unit
) {
    val cropMode = uiState.scaleMode == VideoScaleMode.CROP_PAN
    val fileId = uiState.mediaFile?.id
    var zoom by remember { mutableFloatStateOf(IMAGE_ZOOM_MIN) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // The reset is an effect rather than a key on the remembers above: the gesture node below is
    // created once and captures these state objects once, so a keyed remember would hand it a dead
    // object after the first page turn and zoom would stop responding from the second picture on.
    LaunchedEffect(fileId, cropMode) {
        zoom = IMAGE_ZOOM_MIN
        offset = Offset.Zero
    }

    AsyncImage(
        model = uiState.mediaFile?.uri,
        contentDescription = uiState.mediaFile?.name,
        modifier = Modifier
            .fillMaxSize()
            .cropTransformGestures(enabled = cropMode, currentZoom = { zoom }) { zoomChange, pan ->
                val next = (zoom * zoomChange).coerceIn(IMAGE_ZOOM_MIN, IMAGE_ZOOM_MAX)
                zoom = next
                offset = if (next <= IMAGE_ZOOM_MIN) Offset.Zero else offset + pan
            }
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = offset.x
                translationY = offset.y
            },
        contentScale = if (cropMode) ContentScale.Crop else ContentScale.Fit,
        onState = { state -> onLoadingChange(state is AsyncImagePainter.State.Loading) }
    )
}

/**
 * Pinch to zoom and drag to pan, alive only while the picture is cropped.
 *
 * Written out rather than delegated to `detectTransformGestures`, which consumes a one-finger drag
 * the moment it passes slop and would take the page-turn swipe with it. Here a single pointer is
 * consumed only once the picture is actually enlarged; at neutral zoom nothing is consumed, so the
 * screen's own tap and swipe node keeps paging exactly as it does in fit mode. A press that never
 * moves is never consumed either, which leaves the tap zones working over a zoomed picture.
 */
private fun Modifier.cropTransformGestures(
    enabled: Boolean,
    currentZoom: () -> Float,
    onTransform: (zoomChange: Float, pan: Offset) -> Unit
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val multiTouch = event.changes.size > 1
                if (multiTouch || currentZoom() > IMAGE_ZOOM_MIN) {
                    Timber.d("S2480: crop transform, pointers ${event.changes.size}, zoom ${currentZoom()}")
                    onTransform(event.calculateZoom(), event.calculatePan())
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

/**
 * The bottom panel of the image viewer: name, position, slideshow state and the commands.
 *
 * Extracted from the content composable in S2006 - once the panel became conditional the caller
 * crossed detekt's LongMethod ceiling, and a panel that can be hidden is its own thing anyway.
 * Takes the actions bundle rather than one callback per command, which S2472's back command pushed
 * past detekt's parameter ceiling - the bundle is the reason the bundle exists.
 */
@Composable
private fun ImageBottomPanel(
    uiState: ImageViewerUiState,
    isFavorite: Boolean,
    actions: ImageViewerActions,
    modifier: Modifier = Modifier
) {
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
            text = uiState.mediaFile?.displayName ?: "",
            style = MaterialTheme.typography.caption2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White
        )

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
            onPrevious = actions.onSwipeRight,
            onNext = actions.onSwipeLeft,
            onToggleSlideshow = actions.onToggleSlideshow,
            onTogglePlaybackMode = actions.onTogglePlaybackMode
        )

        ImageSecondaryRow(
            uiState = uiState,
            isFavorite = isFavorite,
            onBack = actions.onBack,
            onToggleFavorite = actions.onToggleFavorite,
            onToggleScaleMode = actions.onToggleScaleMode,
            onFileOperations = actions.onFileOperations
        )

        // S2476: Position counter rendered on its own line under control buttons, matching audio player
        if (uiState.positionText.isNotEmpty()) {
            Timber.d("S2476: ImageViewerScreen rendering positionText %s", uiState.positionText)
            Text(
                text = uiState.positionText,
                style = MaterialTheme.typography.caption3,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }
    }
}

/** Row 1: Previous, Play/Pause (Slideshow), Traversal Mode (Sort/Shuffle/Repeat), Next. */
@Composable
private fun ImageCommandRow(
    uiState: ImageViewerUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onTogglePlaybackMode: () -> Unit
) {
    Timber.d("S2529: ImageViewerScreen ImageCommandRow composed, slideshowActive=${uiState.isSlideshowActive}")
    val slideshowDesc = stringResource(
        if (uiState.isSlideshowActive) R.string.wear_slideshow_stop else R.string.wear_slideshow_start
    )
    val playbackModeIcon = when (uiState.playbackMode) {
        WearPlaybackMode.SEQUENTIAL -> Icons.AutoMirrored.Filled.Sort
        WearPlaybackMode.SHUFFLE -> Icons.Filled.Shuffle
        WearPlaybackMode.LOOP -> Icons.Filled.Repeat
    }
    val playbackModeDesc = stringResource(
        when (uiState.playbackMode) {
            WearPlaybackMode.SEQUENTIAL -> R.string.wear_playback_mode_sequential
            WearPlaybackMode.SHUFFLE -> R.string.wear_playback_mode_shuffle
            WearPlaybackMode.LOOP -> R.string.wear_playback_mode_loop
        }
    )

    PlayerCommandGrid { targetSize ->
        PlayerCommandButton(
            onClick = onPrevious,
            icon = Icons.Filled.SkipPrevious,
            contentDescription = stringResource(R.string.previous),
            modifier = Modifier.size(targetSize)
        )

        PlayerCommandButton(
            onClick = onToggleSlideshow,
            icon = if (uiState.isSlideshowActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = slideshowDesc,
            modifier = Modifier.size(targetSize),
            checked = true,
            iconTint = colorResource(ContentTypeCatalog.tintFor(WearContentType.IMAGE))
        )

        PlayerCommandButton(
            onClick = onTogglePlaybackMode,
            icon = playbackModeIcon,
            contentDescription = playbackModeDesc,
            modifier = Modifier.size(targetSize),
            checked = uiState.playbackMode != WearPlaybackMode.SEQUENTIAL
        )

        PlayerCommandButton(
            onClick = onNext,
            icon = Icons.Filled.SkipNext,
            contentDescription = stringResource(R.string.next),
            modifier = Modifier.size(targetSize)
        )
    }
}

/** Row 2: Back, Favorite, ScaleMode. */
@Composable
private fun ImageSecondaryRow(
    uiState: ImageViewerUiState,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleScaleMode: () -> Unit,
    onFileOperations: () -> Unit
) {
    val favoriteDesc = stringResource(R.string.wear_toggle_favorite)
    val scaleIcon = if (uiState.scaleMode == VideoScaleMode.CROP_PAN) {
        Icons.Filled.AspectRatio
    } else {
        Icons.Filled.CropFree
    }

    PlayerCommandGrid { targetSize ->
        PlayerCommandButton(
            onClick = onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.wear_navigate_back),
            modifier = Modifier.size(targetSize)
        )

        PlayerCommandButton(
            onClick = onToggleFavorite,
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = favoriteDesc,
            modifier = Modifier.size(targetSize),
            checked = isFavorite
        )

        PlayerCommandButton(
            onClick = onFileOperations,
            icon = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.wear_file_op_actions),
            modifier = Modifier.size(targetSize)
        )

        PlayerCommandButton(
            onClick = onToggleScaleMode,
            icon = scaleIcon,
            contentDescription = stringResource(R.string.wear_scale_mode),
            modifier = Modifier.size(targetSize),
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
