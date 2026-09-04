package com.sza.fastmediasorter.wear.ui.browse

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.ui.common.WearAction
import com.sza.fastmediasorter.wear.ui.common.WearActionColumn
import timber.log.Timber

/**
 * Every action this menu can run, in the order it draws them, each beside what it calls.
 *
 * Destructive last: on a round screen the outer rows are the easiest to hit by accident, so the one
 * answer that cannot be taken back is the one furthest from a mis-tap.
 *
 * [WearFileOperationKind.OPEN_ON_PHONE] is absent, and absent by construction rather than filtered
 * out later: a selection has no single original for the phone to open, so this menu has nothing to
 * ask on its behalf and never offers it.
 */
private fun batchActions(callbacks: FileActionsCallbacks): List<Pair<WearFileOperationKind, () -> Unit>> =
    listOf(
        // One entry, not one per receiver: it opens the receiver list, so this menu still holds no
        // list of its own and a receiver added on the phone needs no edit here (strategic 3.3).
        WearFileOperationKind.SEND_TO_RECEIVER to callbacks.onSendToRequested,
        WearFileOperationKind.SEND_TO_PHONE to callbacks.onSendToPhone,
        WearFileOperationKind.MOVE_TO_PHONE to callbacks.onMoveToPhone,
        WearFileOperationKind.RENAME to callbacks.onRenameRequested,
        WearFileOperationKind.DELETE to callbacks.onDeleteRequested
    )

/**
 * What the action menu draws.
 *
 * [allowedOperations] arrives already intersected across the selection: deciding which operations a
 * mixed set permits is the capability policy's answer, not the dialog's, so the menu cannot offer
 * work that half the batch would refuse.
 */
internal data class FileActionsDialogState(
    val selectedCount: Int,
    val totalCount: Int,
    val allowedOperations: Set<WearFileOperationKind>
)

/**
 * What the action menu can ask of the screen that owns the selection.
 *
 * [onDismiss] is the menu's cancel and nothing more: an operation the user never picked is one the
 * user declined, so it puts the screen back as it was and touches no selection and no file.
 */
internal data class FileActionsCallbacks(
    val onSelectAllRequested: () -> Unit,
    val onSendToRequested: () -> Unit,
    val onSendToPhone: () -> Unit,
    val onMoveToPhone: () -> Unit,
    val onRenameRequested: () -> Unit,
    val onDeleteRequested: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * The action menu for the current selection, following the S1833 precedent in `NetworkSourcesScreen`:
 * a long press leads to a menu, and a destructive choice inside it leads to its own confirmation.
 */
@Composable
internal fun FileActionsDialog(
    state: FileActionsDialogState,
    callbacks: FileActionsCallbacks
) {
    Timber.d("S2491: FileActionsDialog selectedCount=%d totalCount=%d", state.selectedCount, state.totalCount)
    val operationActions = batchActions(callbacks)
        .filter { it.first in state.allowedOperations }
        .map { (kind, onClick) ->
            WearAction(
                label = stringResource(kind.labelRes()),
                icon = {
                    Icon(
                        painter = painterResource(kind.iconRes()),
                        contentDescription = null
                    )
                },
                onClick = onClick
            )
        }

    val actions = buildList {
        if (state.selectedCount < state.totalCount) {
            add(
                WearAction(
                    label = stringResource(R.string.wear_select_all),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = null
                        )
                    },
                    onClick = callbacks.onSelectAllRequested
                )
            )
        }
        addAll(operationActions)
    }

    WearActionColumn(
        actions = actions,
        onDismiss = callbacks.onDismiss,
        header = {
            Text(
                text = stringResource(R.string.wear_file_op_title, state.selectedCount),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.title3
            )
        }
    )
}

/**
 * The delete confirmation, named apart from the menu so the destructive answer always costs a second
 * deliberate tap. The title states what will happen rather than asking whether the user is sure.
 */
@Composable
internal fun FileDeleteConfirmDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Alert(
        title = {
            Text(
                text = stringResource(R.string.wear_file_op_delete_confirm, selectedCount),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.title3
            )
        },
        negativeButton = {
            Chip(
                onClick = onDismiss,
                label = { Text(text = stringResource(R.string.cancel)) },
                colors = ChipDefaults.secondaryChipColors()
            )
        },
        positiveButton = {
            Chip(
                onClick = onConfirm,
                label = { Text(text = stringResource(R.string.delete)) },
                colors = ChipDefaults.primaryChipColors()
            )
        }
    )
}

private fun WearFileOperationKind.labelRes(): Int = when (this) {
    WearFileOperationKind.SEND_TO_PHONE -> R.string.wear_file_op_send_to_phone
    WearFileOperationKind.MOVE_TO_PHONE -> R.string.wear_file_op_move_to_phone
    WearFileOperationKind.RENAME -> R.string.wear_file_op_rename
    WearFileOperationKind.DELETE -> R.string.delete
    WearFileOperationKind.OPEN_ON_PHONE -> R.string.wear_file_op_open_on_phone
    WearFileOperationKind.SEND_TO_RECEIVER -> R.string.wear_file_op_send_to
}

@DrawableRes
private fun WearFileOperationKind.iconRes(): Int = when (this) {
    WearFileOperationKind.SEND_TO_PHONE -> R.drawable.ic_copy
    WearFileOperationKind.MOVE_TO_PHONE -> R.drawable.ic_move
    WearFileOperationKind.RENAME -> R.drawable.ic_edit
    WearFileOperationKind.DELETE -> R.drawable.ic_delete
    WearFileOperationKind.OPEN_ON_PHONE -> R.drawable.ic_open_in_new
    WearFileOperationKind.SEND_TO_RECEIVER -> R.drawable.ic_share
}
