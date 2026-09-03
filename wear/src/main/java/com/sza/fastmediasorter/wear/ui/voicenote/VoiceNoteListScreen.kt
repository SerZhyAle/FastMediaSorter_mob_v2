package com.sza.fastmediasorter.wear.ui.voicenote

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendResult
import com.sza.fastmediasorter.wear.ui.common.LongPressChip
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import java.util.Date

private val TITLE_VERTICAL_PADDING = 8.dp
private val TEXT_HORIZONTAL_PADDING = 8.dp
private val ROW_ICON_SIZE = 20.dp
private val ROW_LABEL_GAP = 6.dp
private val PROGRESS_STROKE = 2.dp

/**
 * S1862 / S2161: the notes this watch holds, with sending, deleting and playback on the watch.
 *
 * A single tap plays the voice note directly. Long press opens the action sheet where Play, Send and
 * Delete are available.
 */
@Composable
fun VoiceNoteListScreen(
    onPlayNote: (VoiceNote) -> Unit = {},
    viewModel: VoiceNoteListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberWearListState()
    var actionsFor by remember { mutableStateOf<VoiceNote?>(null) }
    var deleteFor by remember { mutableStateOf<VoiceNote?>(null) }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        NoteListContent(
            uiState = uiState,
            listState = listState,
            onPlayNote = onPlayNote,
            onOpenActions = { note -> actionsFor = note }
        )
    }

    actionsFor?.let { note ->
        NoteActionsDialog(
            note = note,
            onPlay = {
                onPlayNote(note)
                actionsFor = null
            },
            onSend = {
                viewModel.send(note.id)
                actionsFor = null
            },
            onDelete = {
                deleteFor = note
                actionsFor = null
            }
        )
    }

    deleteFor?.let { note ->
        DeleteNoteDialog(
            onCancel = { deleteFor = null },
            onConfirm = {
                viewModel.delete(note.id)
                deleteFor = null
            }
        )
    }

    uiState.lastSendResult?.let { result ->
        SendResultDialog(result = result, onDismiss = viewModel::acknowledgeSendResult)
    }

    uiState.resetNotice?.let { notice ->
        ResetNoticeDialog(
            recoveredNotes = notice.recoveredNotes,
            onDismiss = viewModel::acknowledgeResetNotice
        )
    }
}

@Composable
private fun NoteListContent(
    uiState: VoiceNoteListUiState,
    listState: ScalingLazyListState,
    onPlayNote: (VoiceNote) -> Unit,
    onOpenActions: (VoiceNote) -> Unit
) {
    WearListColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        item {
            Text(
                text = stringResource(R.string.wear_voice_note_list),
                style = MaterialTheme.typography.title2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TITLE_VERTICAL_PADDING),
                textAlign = TextAlign.Center
            )
        }

        if (uiState.notes.isEmpty() && !uiState.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.wear_voice_note_empty),
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TEXT_HORIZONTAL_PADDING)
                )
            }
        }

        items(uiState.notes) { note ->
            NoteRow(
                note = note,
                sending = uiState.sendingNoteId == note.id,
                onPlay = { onPlayNote(note) },
                onOpenActions = { onOpenActions(note) }
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: VoiceNote,
    sending: Boolean,
    onPlay: () -> Unit,
    onOpenActions: () -> Unit
) {
    val stateLabel = stringResource(deliveryLabelOf(note.deliveryState))
    LongPressChip(
        // S2161: a tap plays the note; long press opens the actions sheet where send and delete live.
        // Playback is the thing a person wants to do next (strategic §5.3), so it gets the easy gesture.
        onClick = onPlay,
        onLongClick = onOpenActions,
        label = {
            Text(text = noteTimeLabel(note), maxLines = 1)
            Spacer(modifier = Modifier.width(ROW_LABEL_GAP))
            Text(text = formatVoiceNoteDuration(note.durationMillis), maxLines = 1)
        },
        modifier = Modifier.fillMaxWidth(),
        icon = {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ROW_ICON_SIZE),
                    strokeWidth = PROGRESS_STROKE
                )
            } else {
                Icon(
                    imageVector = deliveryIconOf(note.deliveryState),
                    // The state is spelled out in the secondary label right beside this glyph, so
                    // describing it here as well would make TalkBack say it twice per row.
                    contentDescription = null,
                    modifier = Modifier.size(ROW_ICON_SIZE)
                )
            }
        },
        secondaryLabel = { Text(text = stateLabel, maxLines = 1) },
        colors = ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun NoteActionsDialog(
    note: VoiceNote,
    onPlay: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit
) {
    Alert(
        title = {
            Text(
                text = noteTimeLabel(note),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.title3
            )
        }
    ) {
        item {
            DialogChip(
                labelRes = R.string.wear_voice_note_play,
                onClick = onPlay,
                primary = true
            )
        }
        item {
            DialogChip(
                labelRes = R.string.wear_voice_note_send,
                onClick = onSend,
                primary = false
            )
        }
        item {
            DialogChip(
                labelRes = R.string.wear_voice_note_delete,
                onClick = onDelete,
                primary = false
            )
        }
    }
}

/**
 * The title states what deletion costs rather than asking "are you sure", and the confirming button
 * carries the word "delete" - so the destructive choice is told apart by its words, not by a colour.
 */
@Composable
private fun DeleteNoteDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Alert(
        title = {
            Text(
                text = stringResource(R.string.wear_voice_note_delete_confirm),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.title3
            )
        },
        negativeButton = {
            Chip(
                onClick = onCancel,
                label = { Text(stringResource(R.string.cancel)) },
                colors = ChipDefaults.secondaryChipColors()
            )
        },
        positiveButton = {
            Chip(
                onClick = onConfirm,
                label = { Text(stringResource(R.string.delete)) },
                colors = ChipDefaults.primaryChipColors()
            )
        }
    )
}

/** Section 11 criterion 6: the outcome is readable on the watch, not only in a log. */
@Composable
private fun SendResultDialog(
    result: VoiceNoteSendResult,
    onDismiss: () -> Unit
) {
    Alert(
        title = {
            Text(
                text = stringResource(sendResultLabelOf(result)),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2
            )
        }
    ) {
        item {
            DialogChip(labelRes = android.R.string.ok, onClick = onDismiss, primary = true)
        }
    }
}

/**
 * S2356: shown once after the note index had to be rebuilt.
 *
 * It lives on this screen because the list is the only place the consequence is visible - every
 * delivery badge has gone back to "not sent", which without a word reads as lost data rather than
 * as a recovery (strategic 3.3).
 */
@Composable
private fun ResetNoticeDialog(
    recoveredNotes: Int,
    onDismiss: () -> Unit
) {
    Alert(
        title = {
            Text(
                text = stringResource(R.string.wear_database_reset_notice, recoveredNotes),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2
            )
        }
    ) {
        item {
            DialogChip(labelRes = android.R.string.ok, onClick = onDismiss, primary = true)
        }
    }
}

@Composable
private fun DialogChip(
    @StringRes labelRes: Int,
    onClick: () -> Unit,
    primary: Boolean
) {
    Chip(
        onClick = onClick,
        label = { Text(text = stringResource(labelRes)) },
        modifier = Modifier.fillMaxWidth(),
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
    )
}

/** The watch's own clock format, so a 24-hour watch never shows a note stamped in AM/PM. */
@Composable
private fun noteTimeLabel(note: VoiceNote): String {
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    return timeFormat.format(Date(note.createdAtMillis))
}

@StringRes
private fun deliveryLabelOf(state: VoiceNoteDeliveryState): Int = when (state) {
    VoiceNoteDeliveryState.LOCAL_ONLY -> R.string.wear_voice_note_state_saved
    VoiceNoteDeliveryState.PENDING -> R.string.wear_voice_note_state_pending
    VoiceNoteDeliveryState.SENT -> R.string.wear_voice_note_state_sent
    VoiceNoteDeliveryState.FAILED -> R.string.wear_voice_note_state_failed
}

private fun deliveryIconOf(state: VoiceNoteDeliveryState): ImageVector = when (state) {
    VoiceNoteDeliveryState.LOCAL_ONLY -> Icons.Default.Watch
    VoiceNoteDeliveryState.PENDING -> Icons.Default.Schedule
    VoiceNoteDeliveryState.SENT -> Icons.Default.CloudDone
    VoiceNoteDeliveryState.FAILED -> Icons.Default.ErrorOutline
}

@StringRes
private fun sendResultLabelOf(result: VoiceNoteSendResult): Int = when (result) {
    is VoiceNoteSendResult.Sent -> R.string.wear_voice_note_result_sent
    is VoiceNoteSendResult.PhoneUnreachable -> R.string.wear_voice_note_result_pending
    is VoiceNoteSendResult.TooLarge -> R.string.wear_voice_note_result_too_large
    is VoiceNoteSendResult.Failed -> R.string.wear_voice_note_result_failed
}
