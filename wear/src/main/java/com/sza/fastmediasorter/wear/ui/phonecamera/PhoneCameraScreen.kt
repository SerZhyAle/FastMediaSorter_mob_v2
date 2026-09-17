package com.sza.fastmediasorter.wear.ui.phonecamera

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.CameraLensDto
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraFailure
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraSessionState
import com.sza.fastmediasorter.wear.domain.model.WearStreamPlaybackTarget
import com.sza.fastmediasorter.wear.ui.common.WEAR_LIST_NO_ANCHOR
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState

private val SECTION_GAP = 6.dp
private val STATUS_ICON_SIZE = 32.dp
private val ACTION_ICON_SIZE = 24.dp
private val STATUS_LABEL_TOP_PADDING = 4.dp
private val TEXT_HORIZONTAL_PADDING = 8.dp

/**
 * S2551: where the owner starts, watches and ends a view of the paired phone's camera.
 *
 * The lens list is drawn from what the phone sent rather than from anything declared here, because
 * strategic §5.3 requires the set of cameras to be authored by the phone - a different phone offers
 * a different list, and a list fixed on the watch would be wrong on the first one that differs.
 *
 * Every state names itself in words as well as by a glyph of its own SHAPE, so the running session
 * is distinguishable with colour removed (§3.2).
 */
@Composable
fun PhoneCameraScreen(
    onWatch: (WearStreamPlaybackTarget) -> Unit,
    viewModel: PhoneCameraViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberWearListState(initialCenterItemIndex = WEAR_LIST_NO_ANCHOR)

    // Keyed on the id, so the player is opened once per session rather than on every recomposition
    // that re-reads the same live target.
    val target = state.playbackTarget
    LaunchedEffect(target?.fileId) {
        target?.let(onWatch)
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
            centered = true
        ) {
            item { SessionStatus(session = state.session) }
            item {
                SessionActions(
                    session = state.session,
                    onStart = viewModel::start,
                    onStop = viewModel::stop
                )
            }
            lensList(session = state.session, onSwitchLens = viewModel::switchLens)
        }
    }
}

/**
 * The lens rows, only while a session is live.
 *
 * Rows of the scrolling column rather than a nested list: the round face pushes an outer row off the
 * glass, and only the screen's own scaling column knows how to bring it back (S2470).
 */
private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.lensList(
    session: PhoneCameraSessionState,
    onSwitchLens: (String) -> Unit
) {
    if (session !is PhoneCameraSessionState.Live || session.lenses.isEmpty()) {
        return
    }
    item { SectionHeader(textRes = R.string.wear_phone_camera_lenses) }
    items(session.lenses.size) { index ->
        val lens = session.lenses[index]
        LensChip(
            lens = lens,
            active = lens.id == session.activeLensId,
            onClick = { onSwitchLens(lens.id) }
        )
    }
}

@Composable
private fun SessionStatus(session: PhoneCameraSessionState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = statusIconOf(session),
            // Null on purpose: the label beneath says the same thing, and describing the glyph as
            // well would make TalkBack read the state twice.
            contentDescription = null,
            modifier = Modifier.size(STATUS_ICON_SIZE)
        )
        Text(
            text = stringResource(statusLabelOf(session)),
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = STATUS_LABEL_TOP_PADDING,
                    start = TEXT_HORIZONTAL_PADDING,
                    end = TEXT_HORIZONTAL_PADDING
                )
        )
    }
}

@Composable
private fun SessionActions(
    session: PhoneCameraSessionState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier.width(IntrinsicSize.Min),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (session) {
            is PhoneCameraSessionState.Live -> ActionChip(
                labelRes = R.string.wear_phone_camera_stop,
                icon = Icons.Default.Stop,
                primary = true,
                onClick = onStop
            )
            // A request already on the wire offers nothing: a second start would mint a second id
            // and orphan the answer to the first.
            is PhoneCameraSessionState.Requested -> Unit
            else -> ActionChip(
                labelRes = R.string.wear_phone_camera_start,
                icon = Icons.Default.Videocam,
                primary = true,
                onClick = onStart
            )
        }
    }
}

@Composable
private fun LensChip(lens: CameraLensDto, active: Boolean, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(text = lensLabelOf(lens)) },
        icon = {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                // The row's own label names the lens; the glyph repeats it for the eye only.
                contentDescription = null,
                modifier = Modifier.size(ACTION_ICON_SIZE)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = if (active) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun SectionHeader(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.caption1,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TEXT_HORIZONTAL_PADDING)
    )
}

/**
 * The phone sends a key rather than a word, so the wearer reads the lens in the watch's own locale
 * even when the two devices are set to different languages. An unknown key falls back to the facing
 * the platform reported, which is still a word the owner can act on.
 */
@Composable
private fun lensLabelOf(lens: CameraLensDto): String = when (lens.labelKey) {
    LENS_KEY_BACK -> stringResource(R.string.wear_phone_camera_lens_back)
    LENS_KEY_FRONT -> stringResource(R.string.wear_phone_camera_lens_front)
    else -> lens.facing
}

@StringRes
private fun statusLabelOf(session: PhoneCameraSessionState): Int = when (session) {
    is PhoneCameraSessionState.Idle -> R.string.wear_phone_camera_idle_caption
    is PhoneCameraSessionState.Requested -> R.string.wear_phone_camera_requested
    is PhoneCameraSessionState.Live -> R.string.wear_phone_camera_live
    is PhoneCameraSessionState.Refused -> failureLabelOf(session.reason)
}

/**
 * Each refusal names what the owner can do about it, and the Wi-Fi one is why the whole enum exists
 * rather than one shared sentence: strategic criterion 4 requires "turn Wi-Fi on" where a narrow
 * link would say something the owner cannot act on.
 */
@StringRes
private fun failureLabelOf(reason: PhoneCameraFailure): Int = when (reason) {
    PhoneCameraFailure.NO_PHONE -> R.string.wear_phone_camera_failed_no_phone
    PhoneCameraFailure.NOT_ON_WIFI -> R.string.wear_phone_camera_failed_not_on_wifi
    PhoneCameraFailure.NO_NETWORK -> R.string.wear_phone_camera_failed_no_network
    PhoneCameraFailure.NARROW_LINK -> R.string.wear_phone_camera_failed_narrow_link
    PhoneCameraFailure.NOT_ASKED -> R.string.wear_phone_camera_failed_not_asked
    PhoneCameraFailure.NOT_ARMED -> R.string.wear_phone_camera_failed_not_armed
    PhoneCameraFailure.DECLINED -> R.string.wear_phone_camera_failed_declined
    PhoneCameraFailure.EXPIRED -> R.string.wear_phone_camera_failed_expired
    PhoneCameraFailure.CAPTURE_FAILED -> R.string.wear_phone_camera_failed_capture
    PhoneCameraFailure.STOPPED -> R.string.wear_phone_camera_stopped
    PhoneCameraFailure.ENDED -> R.string.wear_phone_camera_ended
    PhoneCameraFailure.BUSY -> R.string.wear_phone_camera_failed_busy
    PhoneCameraFailure.NOT_SUPPORTED -> R.string.wear_phone_camera_failed_not_supported
    PhoneCameraFailure.UNKNOWN -> R.string.wear_phone_camera_failed_unknown
}

/** The live glyph differs in SHAPE from every other state's, not only in tint (§3.2). */
private fun statusIconOf(session: PhoneCameraSessionState): ImageVector = when (session) {
    is PhoneCameraSessionState.Idle -> Icons.Default.Videocam
    is PhoneCameraSessionState.Requested -> Icons.Default.HourglassEmpty
    is PhoneCameraSessionState.Live -> Icons.Default.PhotoCamera
    is PhoneCameraSessionState.Refused -> Icons.Default.ErrorOutline
}

@Composable
private fun ActionChip(
    @StringRes labelRes: Int,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        label = { Text(text = stringResource(labelRes)) },
        icon = {
            Icon(
                imageVector = icon,
                // The chip's own label names the action; the glyph repeats it for the eye only.
                contentDescription = null,
                modifier = Modifier.size(ACTION_ICON_SIZE)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
    )
}

/** The keys the phone's lens enumeration sends. Declared once so the two ends compare as symbols. */
internal const val LENS_KEY_BACK = "lens_back"
internal const val LENS_KEY_FRONT = "lens_front"
