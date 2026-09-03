package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile

/**
 * The one long-press menu every file surface opens.
 *
 * It renders the set it is handed and asks nothing itself, so the capability policy stays the single
 * answer to "what may this file be asked to do" - a menu that offers an operation the source will
 * refuse is the trust failure that policy exists to prevent (strategic ADR-4).
 */
@Composable
fun WearFileActionsDialog(
    file: WearMediaFile,
    allowed: Set<WearFileOperationKind>,
    onPick: (WearFileOperationKind) -> Unit,
    onDismiss: () -> Unit,
    onUnmark: (() -> Unit)? = null
) {
    if (allowed.isEmpty() && onUnmark == null) {
        // Reporting the dismissal rather than opening an empty dialog lets the caller, which already
        // holds the same set, put a message where the menu would have been.
        LaunchedEffect(file.id) { onDismiss() }
        return
    }

    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberWearListState()
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                val title = stringResource(R.string.wear_file_actions_for, file.name)
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = TITLE_GAP)
                        .semantics { contentDescription = title }
                )
            }

            items(ACTION_ORDER.filter { it in allowed }) { kind ->
                val label = stringResource(labelOf(kind))
                ActionChip(
                    label = label,
                    iconRes = iconOf(kind),
                    onClick = { onPick(kind) }
                )
            }

            // Unmarking is list membership rather than a file operation, so it never passes through
            // the capability policy and is drawn after everything the policy did decide.
            if (onUnmark != null) {
                item {
                    ActionChip(
                        label = stringResource(R.string.wear_favourites_unmark),
                        onClick = onUnmark
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    @DrawableRes iconRes: Int? = null,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        label = { Text(label) },
        icon = iconRes?.let {
            {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        colors = ChipDefaults.secondaryChipColors()
    )
}

@StringRes
private fun labelOf(kind: WearFileOperationKind): Int = when (kind) {
    WearFileOperationKind.SEND_TO_PHONE -> R.string.wear_file_op_send_to_phone
    WearFileOperationKind.MOVE_TO_PHONE -> R.string.wear_file_op_move_to_phone
    WearFileOperationKind.DELETE -> R.string.delete
    WearFileOperationKind.RENAME -> R.string.wear_file_op_rename
    WearFileOperationKind.OPEN_ON_PHONE -> R.string.wear_file_op_open_on_phone
    WearFileOperationKind.SEND_TO_RECEIVER -> R.string.wear_file_op_send_to
}

@DrawableRes
private fun iconOf(kind: WearFileOperationKind): Int = when (kind) {
    WearFileOperationKind.SEND_TO_PHONE -> R.drawable.ic_copy
    WearFileOperationKind.MOVE_TO_PHONE -> R.drawable.ic_move
    WearFileOperationKind.DELETE -> R.drawable.ic_delete
    WearFileOperationKind.RENAME -> R.drawable.ic_edit
    WearFileOperationKind.OPEN_ON_PHONE -> R.drawable.ic_open_in_new
    WearFileOperationKind.SEND_TO_RECEIVER -> R.drawable.ic_share
}


private val TITLE_GAP = 8.dp

/**
 * Destructive last, the order and the reason `ui/browse/FileActionsDialog.kt` already recorded: on a
 * round screen the outer rows are the easiest to hit by accident, so the one answer that cannot be
 * taken back sits furthest from a mis-tap.
 *
 * Opening on the phone sits with the two actions above it because it leaves the watch as they do, and
 * ahead of renaming because renaming keeps the user here (strategic 3.4 item 8).
 */
private val ACTION_ORDER = listOf(
    // First, by the owner's ruling in strategic 3.4: «Send to..» leads, deletion still trails.
    WearFileOperationKind.SEND_TO_RECEIVER,
    WearFileOperationKind.SEND_TO_PHONE,
    WearFileOperationKind.MOVE_TO_PHONE,
    WearFileOperationKind.OPEN_ON_PHONE,
    WearFileOperationKind.RENAME,
    WearFileOperationKind.DELETE
)
