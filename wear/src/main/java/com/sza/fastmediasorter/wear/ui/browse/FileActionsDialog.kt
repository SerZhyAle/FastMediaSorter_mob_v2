package com.sza.fastmediasorter.wear.ui.browse

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    val allowedOperations: Set<WearFileOperationKind>
)

/** What the action menu can ask of the screen that owns the selection. */
internal data class FileActionsCallbacks(
    val onSendToPhone: () -> Unit,
    val onMoveToPhone: () -> Unit,
    val onRenameRequested: () -> Unit,
    val onDeleteRequested: () -> Unit
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
    Alert(
        title = {
            Text(
                text = stringResource(R.string.wear_file_op_title, state.selectedCount),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.title3
            )
        }
    ) {
        batchActions(callbacks).filter { it.first in state.allowedOperations }.forEach { (kind, onClick) ->
            item {
                ActionChip(kind = kind, onClick = onClick)
            }
        }
    }
}

/**
 * Every action carries its own word and its own glyph. Marking the destructive one by colour alone
 * would leave the distinction unreadable to the accessibility requirement in strategic 3.2.
 */
@Composable
private fun ActionChip(
    kind: WearFileOperationKind,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        label = { Text(text = stringResource(kind.labelRes())) },
        icon = {
            Icon(
                imageVector = kind.icon(),
                contentDescription = null
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
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
}

private fun WearFileOperationKind.icon(): ImageVector = when (this) {
    WearFileOperationKind.SEND_TO_PHONE -> Icons.AutoMirrored.Filled.Send
    WearFileOperationKind.MOVE_TO_PHONE -> Icons.Default.PhoneAndroid
    WearFileOperationKind.RENAME -> Icons.Default.Edit
    WearFileOperationKind.DELETE -> Icons.Default.Delete
    WearFileOperationKind.OPEN_ON_PHONE -> Icons.AutoMirrored.Filled.OpenInNew
}
