package com.sza.fastmediasorter.wear.ui.listen

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
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.listen.ListenSessionState
import com.sza.fastmediasorter.wear.ui.common.WEAR_LIST_NO_ANCHOR
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import timber.log.Timber

private val SECTION_GAP = 6.dp
private val STATUS_ICON_SIZE = 32.dp
private val ACTION_ICON_SIZE = 24.dp
private val STATUS_LABEL_TOP_PADDING = 4.dp
private val TEXT_HORIZONTAL_PADDING = 8.dp

/**
 * S2550 Pillar F / S2941: the window that makes the feature legal, now auto-starting the microphone.
 *
 * S2941 replaces the confirm/decline tap with an automatic start: the window opens via
 * `setFullScreenIntent` and calls `confirm()` from `LaunchedEffect` while in the `Idle` state, so the
 * microphone starts from a foreground context without user interaction. While the session runs the
 * screen says so in words and in a glyph, with the state also on the accessibility tree - §3.2 requires
 * an active-transmission indicator distinguishable by more than colour, and there is no action here
 * that hides it. The "dim screen" button finishes the activity; the session continues in the
 * foreground notification, whose `contentIntent` reopens this window.
 */
@Composable
fun ListenRequestScreen(
    onFinished: () -> Unit,
    onDimScreen: () -> Unit,
    viewModel: ListenRequestViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberWearListState(initialCenterItemIndex = WEAR_LIST_NO_ANCHOR)

    LaunchedEffect(Unit) {
        if (state is ListenSessionState.Idle) {
            Timber.d("S2941: auto-starting listen from full-screen intent window")
            viewModel.confirm()
        }
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
            item { ListenStatus(state = state) }
            item {
                ListenActions(
                    state = state,
                    onDimScreen = onDimScreen,
                    onStop = {
                        viewModel.stopListening()
                        onFinished()
                    },
                    onFinished = onFinished
                )
            }
        }
    }
}

/**
 * The live state carries three signals that survive the loss of colour: the glyph, the words beneath
 * it, and a `contentDescription` on the merged row saying the microphone is on. The tint is a fourth
 * added on top, never a replacement - a watch with a colour filter still reads the first three.
 */
@Composable
private fun ListenStatus(state: ListenSessionState) {
    val live = state is ListenSessionState.Live
    // Resolved outside the semantics lambda, which is not a composable scope and cannot read a
    // resource; the description is only applied while the microphone is actually open.
    val microphoneOn = stringResource(R.string.wear_listen_active_description)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (live) {
                    contentDescription = microphoneOn
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
                .padding(top = STATUS_LABEL_TOP_PADDING, start = TEXT_HORIZONTAL_PADDING, end = TEXT_HORIZONTAL_PADDING)
        )
    }
}

/**
 * A live session offers two actions: "dim screen" (finish the activity, session continues in the
 * notification) and "stop listening" (end the session on both sides). There is no dismiss and no
 * setting that hides the indicator above: Pillar F makes covert listening structurally impossible
 * rather than merely discouraged. A failed start offers a close action; `Idle` is transient
 * (auto-start fires in `LaunchedEffect`), and `Starting` shows no action while the microphone opens.
 */
@Composable
private fun ListenActions(
    state: ListenSessionState,
    onDimScreen: () -> Unit,
    onStop: () -> Unit,
    onFinished: () -> Unit
) {
    Column(
        modifier = Modifier.width(IntrinsicSize.Min),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            is ListenSessionState.Live -> {
                ActionChip(
                    labelRes = R.string.wear_listen_dim_screen,
                    icon = Icons.Default.BrightnessLow,
                    primary = false,
                    onClick = onDimScreen
                )
                ActionChip(
                    labelRes = R.string.wear_listen_stop,
                    icon = Icons.Default.Stop,
                    primary = true,
                    onClick = onStop
                )
            }
            is ListenSessionState.Starting -> Unit
            is ListenSessionState.Idle -> Unit
            is ListenSessionState.Failed -> ActionChip(
                labelRes = R.string.wear_listen_request_decline,
                icon = Icons.Default.Close,
                primary = false,
                onClick = onFinished
            )
        }
    }
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
private fun statusLabelOf(state: ListenSessionState): Int = when (state) {
    is ListenSessionState.Idle -> R.string.wear_listen_request_caption
    is ListenSessionState.Starting -> R.string.wear_listen_starting
    is ListenSessionState.Live -> R.string.wear_listen_active_label
    is ListenSessionState.Failed -> R.string.wear_listen_failed
}

/**
 * The live glyph differs in SHAPE from every other state's, not only in tint: §3.2 requires the
 * indicator to be readable with colour removed, which a same-glyph-different-colour pair is not.
 */
private fun statusIconOf(state: ListenSessionState): ImageVector = when (state) {
    is ListenSessionState.Idle -> Icons.Default.Mic
    is ListenSessionState.Starting -> Icons.Default.HourglassEmpty
    is ListenSessionState.Live -> Icons.Default.FiberManualRecord
    is ListenSessionState.Failed -> Icons.Default.ErrorOutline
}
