package com.sza.fastmediasorter.wear.ui.broadcast

import android.Manifest
import android.os.Build
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastFailure
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastSessionState
import com.sza.fastmediasorter.wear.ui.common.WEAR_LIST_NO_ANCHOR
import com.sza.fastmediasorter.wear.ui.common.WearDimOverlay
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.common.wearScreenOffIcon
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme

private val SECTION_GAP = 6.dp
private val STATUS_ICON_SIZE = 32.dp
private val ACTION_ICON_SIZE = 24.dp
private val STATUS_LABEL_TOP_PADDING = 4.dp
private val TEXT_HORIZONTAL_PADDING = 8.dp

/**
 * S2509: where the owner opens an audio broadcast from the wrist and where it is ended in one tap.
 *
 * Before it starts, the screen states what starting means - an open microphone heard by anyone on the
 * same Wi-Fi - so the start is a decision rather than a side effect. While it runs, the state is
 * carried by a glyph of a different SHAPE, by words beneath it and by a content description on the
 * merged row, because strategic §3.2 requires the active indicator to survive the loss of colour.
 *
 * Leaving this screen does not stop anything, which is the point: the session belongs to a foreground
 * service, and the notification carries the same stop action for the owner who walked away.
 *
 * S2878: a live session offers the moon as its last action - it covers the screen with the shared
 * black sheet so an open microphone stops lighting up the room. The sheet carries no display hold,
 * unlike the player's: the broadcast is a service a sleeping display does not disturb, and the
 * display going to sleep is the point, not a side effect. The dimmed state belongs to this screen -
 * leaving the screen leaves it, while the session itself stays.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WearBroadcastScreen(
    onShowQr: () -> Unit,
    viewModel: WearBroadcastViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberWearListState(initialCenterItemIndex = WEAR_LIST_NO_ANCHOR)
    val permissionsState = rememberMultiplePermissionsState(broadcastPermissions())
    var dimmed by rememberSaveable { mutableStateOf(false) }
    // Only the microphone gates the broadcast. A denied POST_NOTIFICATIONS costs the ongoing
    // notification and its stop action, which the screen still offers - it must not block the feature.
    val microphoneGranted = permissionsState.permissions
        .first { it.permission == Manifest.permission.RECORD_AUDIO }
        .status
        .isGranted

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = if (dimmed) {
            null
        } else {
            { PositionIndicator(listState) }
        },
        showTimeText = !dimmed
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
            centered = true
        ) {
            item { BroadcastStatus(state = state) }
            if (!microphoneGranted) {
                item { BlockerText(textRes = R.string.wear_voice_note_permission_required) }
            }
            item {
                BroadcastActions(
                    state = state,
                    microphoneGranted = microphoneGranted,
                    onStart = viewModel::start,
                    onStop = viewModel::stop,
                    onGrant = permissionsState::launchMultiplePermissionRequest,
                    onShowQr = onShowQr,
                    onDimScreen = {
                        dimmed = true
                    }
                )
            }
        }
        if (dimmed) {
            WearDimOverlay(
                onExit = {
                    dimmed = false
                }
            )
        }
    }
}

@Composable
private fun BroadcastStatus(state: WearBroadcastSessionState) {
    val live = state is WearBroadcastSessionState.Live
    // Resolved outside the semantics lambda, which is not a composable scope and cannot read a
    // resource; the description is only applied while the microphone is actually open.
    val onAir = stringResource(R.string.wear_broadcast_active_description)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (live) {
                    contentDescription = onAir
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = statusIconOf(state),
            // Null on purpose: the label directly beneath says the same thing, and describing the
            // glyph as well would make TalkBack read the state twice.
            contentDescription = null,
            tint = if (live) WearAppTheme.colors.recording else LocalContentColor.current,
            modifier = Modifier.size(STATUS_ICON_SIZE)
        )
        Text(
            text = stringResource(statusLabelOf(state)),
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

/**
 * A live broadcast offers the stop first, the QR second and the moon last. The order is the ranking:
 * ending an open microphone is the action that must never be hunted for, sharing can wait a row, and
 * blanking the screen can wait behind sharing because the owner has usually already looked away.
 */
@Composable
private fun BroadcastActions(
    state: WearBroadcastSessionState,
    microphoneGranted: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onGrant: () -> Unit,
    onShowQr: () -> Unit,
    onDimScreen: () -> Unit
) {
    Column(
        modifier = Modifier.width(IntrinsicSize.Min),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            is WearBroadcastSessionState.Live -> {
                ActionChip(
                    labelRes = R.string.wear_broadcast_stop,
                    icon = Icons.Default.Stop,
                    primary = true,
                    onClick = onStop
                )
                ActionChip(
                    labelRes = R.string.wear_broadcast_share_qr,
                    icon = Icons.Default.QrCode,
                    primary = false,
                    onClick = onShowQr
                )
                ActionChip(
                    labelRes = R.string.wear_screen_off,
                    icon = wearScreenOffIcon(),
                    primary = false,
                    onClick = onDimScreen
                )
            }
            is WearBroadcastSessionState.Starting -> Unit
            else -> IdleActions(
                microphoneGranted = microphoneGranted,
                onStart = onStart,
                onGrant = onGrant
            )
        }
    }
}

@Composable
private fun IdleActions(microphoneGranted: Boolean, onStart: () -> Unit, onGrant: () -> Unit) {
    if (microphoneGranted) {
        ActionChip(
            labelRes = R.string.wear_broadcast_start,
            icon = Icons.Default.Cast,
            primary = true,
            onClick = onStart
        )
    } else {
        ActionChip(
            labelRes = R.string.wear_voice_note_permission_grant,
            icon = Icons.Default.Mic,
            primary = true,
            onClick = onGrant
        )
    }
}

@Composable
private fun BlockerText(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TEXT_HORIZONTAL_PADDING)
    )
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
        // Fills the intrinsic-width column above, so every chip ends up the width of the widest.
        modifier = Modifier.fillMaxWidth(),
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
    )
}

@StringRes
private fun statusLabelOf(state: WearBroadcastSessionState): Int = when (state) {
    is WearBroadcastSessionState.Idle -> R.string.wear_broadcast_idle_caption
    is WearBroadcastSessionState.Starting -> R.string.wear_broadcast_starting
    is WearBroadcastSessionState.Live -> R.string.wear_broadcast_live_label
    is WearBroadcastSessionState.Failed -> failureLabelOf(state.reason)
}

/**
 * Each refusal names what the owner can do about it. "No Wi-Fi" is actionable and "the microphone
 * would not open" is not - but a shared word for both would make the first one unactionable too.
 */
@StringRes
private fun failureLabelOf(reason: WearBroadcastFailure): Int = when (reason) {
    WearBroadcastFailure.NO_USABLE_NETWORK -> R.string.wear_broadcast_failed_no_network
    WearBroadcastFailure.CAPTURE_FAILED -> R.string.wear_broadcast_failed_capture
}

/**
 * The live glyph differs in SHAPE from every other state's, not only in tint: §3.2 requires the
 * indicator to be readable with colour removed, which a same-glyph-different-colour pair is not.
 */
private fun statusIconOf(state: WearBroadcastSessionState): ImageVector = when (state) {
    is WearBroadcastSessionState.Idle -> Icons.Default.Cast
    is WearBroadcastSessionState.Starting -> Icons.Default.HourglassEmpty
    is WearBroadcastSessionState.Live -> Icons.Default.FiberManualRecord
    is WearBroadcastSessionState.Failed -> Icons.Default.ErrorOutline
}

/**
 * POST_NOTIFICATIONS travels with the microphone, as it does for the recorder: on API 33+ a denial
 * costs the ongoing notification, which for a broadcast is also the stop action reachable from Home.
 */
private fun broadcastPermissions(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}
