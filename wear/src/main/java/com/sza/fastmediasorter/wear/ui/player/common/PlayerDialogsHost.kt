@file:Suppress("MatchingDeclarationName")

package com.sza.fastmediasorter.wear.ui.player.common

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.ui.browse.FileActionsCallbacks
import com.sza.fastmediasorter.wear.ui.browse.FileActionsDialog
import com.sza.fastmediasorter.wear.ui.browse.FileActionsDialogState
import com.sza.fastmediasorter.wear.ui.browse.FileDeleteConfirmDialog
import com.sza.fastmediasorter.wear.ui.browse.OperationRunDialog
import com.sza.fastmediasorter.wear.ui.browse.fileOperationActions
import com.sza.fastmediasorter.wear.ui.common.ReceiverListDialog
import com.sza.fastmediasorter.wear.ui.common.WearAction
import com.sza.fastmediasorter.wear.ui.common.rememberWearRenameInput

/**
 * Which of the player's dialogs are up, and how each one is put away.
 *
 * Bundled the way `BrowseDialogVisibilities` already bundles the browse screen's six: the three
 * players hand this host an identical set, and passing it as one value keeps the three call sites
 * from drifting apart a flag at a time.
 */
data class PlayerDialogVisibilities(
    val showActions: Boolean,
    val showDeleteConfirm: Boolean,
    val showReceivers: Boolean,
    val onActionsVisibilityChange: (Boolean) -> Unit,
    val onDeleteVisibilityChange: (Boolean) -> Unit,
    val onReceiversVisibilityChange: (Boolean) -> Unit
)

/**
 * What the player's file operations do when picked, wherever they are picked from.
 *
 * S3118: the video player draws these operations as entries of its own menu while the other players
 * open the file-action dialog, and both paths run the same callbacks - a second copy of "rename asks
 * for a name, delete asks for a confirmation" is how the two surfaces would answer differently.
 */
@Composable
private fun rememberPlayerFileActionCallbacks(
    operations: PlayerFileOperationsManager,
    visibilities: PlayerDialogVisibilities,
    currentFileName: String?
): FileActionsCallbacks {
    val requestRename = rememberWearRenameInput { newName ->
        operations.runOperation(WearFileOperation.Rename(newName))
    }
    return FileActionsCallbacks(
        onSelectAllRequested = {},
        onSendToRequested = {
            visibilities.onActionsVisibilityChange(false)
            visibilities.onReceiversVisibilityChange(true)
        },
        onSendToPhone = {
            visibilities.onActionsVisibilityChange(false)
            operations.runOperation(WearFileOperation.SendToPhone)
        },
        onMoveToPhone = {
            visibilities.onActionsVisibilityChange(false)
            operations.runOperation(WearFileOperation.MoveToPhone)
        },
        onCopyToWatch = {
            visibilities.onActionsVisibilityChange(false)
            operations.runOperation(WearFileOperation.CopyToWatch)
        },
        onMoveToWatch = {
            visibilities.onActionsVisibilityChange(false)
            operations.runOperation(WearFileOperation.MoveToWatch)
        },
        onRenameRequested = {
            visibilities.onActionsVisibilityChange(false)
            requestRename(currentFileName)
        },
        onDeleteRequested = {
            visibilities.onActionsVisibilityChange(false)
            visibilities.onDeleteVisibilityChange(true)
        },
        onDismiss = { visibilities.onActionsVisibilityChange(false) }
    )
}

/**
 * The file operations as menu entries, in the dialog's own order and with its labels and icons.
 *
 * The set comes from [PlayerFileOperationsManager.allowedOperations], which is the file capability
 * policy's answer to "what may this file be asked to do" - a caller that filtered the list itself
 * would be offering work the policy refuses. Destructive last, as in the dialog: on a round screen
 * the outer rows are the easiest to hit by accident.
 */
@Composable
fun rememberPlayerFileActionEntries(
    operations: PlayerFileOperationsManager,
    visibilities: PlayerDialogVisibilities,
    currentFileName: String?
): List<WearAction> {
    val allowedOperations by operations.allowedOperations.collectAsStateWithLifecycle()
    val callbacks = rememberPlayerFileActionCallbacks(operations, visibilities, currentFileName)
    return fileOperationActions(allowedOperations, callbacks)
}

@Composable
fun PlayerDialogsHost(
    operations: PlayerFileOperationsManager,
    visibilities: PlayerDialogVisibilities,
    currentFileName: String?
) {
    val showActions = visibilities.showActions
    val showDeleteConfirm = visibilities.showDeleteConfirm
    val showReceivers = visibilities.showReceivers
    val onDeleteVisibilityChange = visibilities.onDeleteVisibilityChange
    val onReceiversVisibilityChange = visibilities.onReceiversVisibilityChange
    val allowedOperations by operations.allowedOperations.collectAsStateWithLifecycle()
    val receivers by operations.sendToReceivers.collectAsStateWithLifecycle()
    val run by operations.operationRun.collectAsStateWithLifecycle()

    val callbacks = rememberPlayerFileActionCallbacks(operations, visibilities, currentFileName)

    if (showActions) {
        FileActionsDialog(
            state = FileActionsDialogState(
                selectedCount = 1,
                totalCount = 1,
                allowedOperations = allowedOperations
            ),
            callbacks = callbacks
        )
    }

    if (showReceivers) {
        ReceiverListDialog(
            receivers = receivers,
            onPick = { entry ->
                onReceiversVisibilityChange(false)
                operations.runOperation(WearFileOperation.SendToReceiver(entry.id))
            },
            onDismiss = { onReceiversVisibilityChange(false) }
        )
    }

    if (showDeleteConfirm) {
        FileDeleteConfirmDialog(
            selectedCount = 1,
            onConfirm = {
                onDeleteVisibilityChange(false)
                operations.runOperation(WearFileOperation.Delete)
            },
            onDismiss = { onDeleteVisibilityChange(false) }
        )
    }

    if (!run.isIdle) {
        OperationRunDialog(
            run = run,
            onCancel = operations::cancelOperation,
            onDismiss = operations::dismissOperationResults
        )
    }

    PlayerConsentPrompt(operations = operations)
}

@Composable
private fun PlayerConsentPrompt(operations: PlayerFileOperationsManager) {
    val consentRequest by operations.consentRequest.collectAsStateWithLifecycle()
    var launched by rememberSaveable { mutableStateOf(false) }
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        operations.onConsentAnswered(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(consentRequest) {
        val request = consentRequest
        when {
            request == null -> launched = false
            !launched -> {
                launched = true
                consentLauncher.launch(IntentSenderRequest.Builder(request).build())
            }
        }
    }
}
