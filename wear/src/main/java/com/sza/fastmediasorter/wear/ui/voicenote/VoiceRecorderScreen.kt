package com.sza.fastmediasorter.wear.ui.voicenote

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
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
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingErrorReason
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingState
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme

private val SECTION_GAP = 6.dp
private val STATUS_ICON_SIZE = 32.dp
private val ACTION_ICON_SIZE = 24.dp
private val STATUS_LABEL_TOP_PADDING = 4.dp
private val TEXT_HORIZONTAL_PADDING = 8.dp

/**
 * S1862: the recorder itself - one status, one action, and the way to the notes already recorded.
 *
 * The microphone is asked for here rather than at the app's entrance: no other screen of this module
 * needs it, and blocking the whole launch on a permission one feature uses would be wrong. The screen
 * only shows and dispatches; the session belongs to a foreground service (ADR-4).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceRecorderScreen(
    navController: NavController,
    onPlayNote: (VoiceNote) -> Unit = {},
    viewModel: VoiceRecorderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberWearListState()
    val permissionsState = rememberMultiplePermissionsState(recorderPermissions())
    // Only the microphone gates recording. A denied POST_NOTIFICATIONS costs the ongoing
    // notification and nothing else, so it must not stand between the user and a recording.
    val microphoneGranted = permissionsState.permissions
        .first { it.permission == Manifest.permission.RECORD_AUDIO }
        .status
        .isGranted

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
            item { RecorderStatus(state = uiState.recording) }
            item {
                RecorderActionChip(
                    state = uiState.recording,
                    startAllowed = microphoneGranted && uiState.hasRoomToRecord,
                    onStart = viewModel::startRecording,
                    onStop = viewModel::stopRecording
                )
            }
            // S2161: play-back control visible when idle and a note exists. Hidden while recording
            // so the recording state's own controls stay unambiguous (strategic §5.3).
            val recentNote = uiState.mostRecentNote
            if (uiState.recording is VoiceRecordingState.Idle && recentNote != null) {
                item {
                    SecondaryChip(
                        labelRes = R.string.wear_voice_note_play,
                        onClick = { onPlayNote(recentNote) }
                    )
                }
            }
            if (!microphoneGranted) {
                item { BlockerText(textRes = R.string.wear_voice_note_permission_required) }
                item {
                    SecondaryChip(
                        labelRes = R.string.wear_voice_note_permission_grant,
                        onClick = permissionsState::launchMultiplePermissionRequest
                    )
                }
            }
            if (!uiState.hasRoomToRecord) {
                item { BlockerText(textRes = R.string.wear_voice_note_no_space) }
            }
            item {
                SecondaryChip(
                    labelRes = R.string.wear_voice_note_open_list,
                    onClick = { navController.navigate(WearRoutes.VOICE_NOTES) }
                )
            }
        }
    }
}

/**
 * POST_NOTIFICATIONS travels with the microphone rather than being asked for on its own: the only
 * notification this app raises is the recorder's, so there is no second moment where asking would
 * make sense, and on API 33+ a denial means the foreground service runs with nothing on screen to
 * say that the watch is listening.
 */
private fun recorderPermissions(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Section 3.2 requires every state to be legible without colour, so the glyph never carries the
 * state alone - it is always paired with the words below it, and the two are one accessibility stop.
 *
 * S2161 tints the dot and the counter while recording. The tone is a THIRD signal added on top of
 * the glyph swap and the words, never a replacement for either: a watch with a colour filter on, or
 * a user listening to TalkBack, still gets the state from the two signals that were already here.
 */
@Composable
private fun RecorderStatus(state: VoiceRecordingState) {
    val recording = state is VoiceRecordingState.Recording
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = statusIconOf(state),
            // Null on purpose: the label directly beneath says the same thing, and describing the
            // glyph as well would make TalkBack read the state twice.
            contentDescription = null,
            tint = if (recording) WearAppTheme.colors.recording else LocalContentColor.current,
            modifier = Modifier.size(STATUS_ICON_SIZE)
        )
        Text(
            text = stringResource(statusLabelOf(state)),
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = STATUS_LABEL_TOP_PADDING)
        )
        if (state is VoiceRecordingState.Recording) {
            Text(
                text = formatVoiceNoteDuration(state.elapsedMillis),
                style = MaterialTheme.typography.title2,
                color = WearAppTheme.colors.recording,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RecorderActionChip(
    state: VoiceRecordingState,
    startAllowed: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val stopping = state is VoiceRecordingState.Recording
    Chip(
        onClick = if (stopping) onStop else onStart,
        // Finishing refuses both: the container is still being flushed, so a second stop would race
        // the first and a start would open the microphone over a session that has not let go of it.
        enabled = stopping || (startAllowed && state !is VoiceRecordingState.Finishing),
        label = {
            Text(
                text = stringResource(
                    if (stopping) R.string.wear_voice_note_stop else R.string.wear_voice_note_start
                )
            )
        },
        icon = {
            Icon(
                imageVector = if (stopping) Icons.Default.Stop else Icons.Default.Mic,
                // The chip's own label names the action; the glyph repeats it for the eye only.
                contentDescription = null,
                modifier = Modifier.size(ACTION_ICON_SIZE)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.primaryChipColors()
    )
}

/** Section 7 asks for a named reason instead of a button that silently refuses to work. */
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
private fun SecondaryChip(
    @StringRes labelRes: Int,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        label = { Text(text = stringResource(labelRes)) },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}

@StringRes
private fun statusLabelOf(state: VoiceRecordingState): Int = when (state) {
    is VoiceRecordingState.Idle -> R.string.wear_voice_note_status_idle
    is VoiceRecordingState.Recording -> R.string.wear_voice_note_recording
    is VoiceRecordingState.Finishing -> R.string.wear_voice_note_saving
    is VoiceRecordingState.Error -> errorLabelOf(state.reason)
}

@StringRes
private fun errorLabelOf(reason: VoiceRecordingErrorReason): Int = when (reason) {
    VoiceRecordingErrorReason.PERMISSION_DENIED -> R.string.wear_voice_note_permission_required
    VoiceRecordingErrorReason.NO_FREE_SPACE -> R.string.wear_voice_note_no_space
    VoiceRecordingErrorReason.RECORDER_UNAVAILABLE -> R.string.wear_voice_note_error_recorder
    VoiceRecordingErrorReason.NOTHING_RECORDED -> R.string.wear_voice_note_error_empty
}

private fun statusIconOf(state: VoiceRecordingState): ImageVector = when (state) {
    is VoiceRecordingState.Idle -> Icons.Default.Mic
    is VoiceRecordingState.Recording -> Icons.Default.FiberManualRecord
    is VoiceRecordingState.Finishing -> Icons.Default.HourglassEmpty
    is VoiceRecordingState.Error -> Icons.Default.ErrorOutline
}
