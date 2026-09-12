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
import com.sza.fastmediasorter.wear.ui.common.ReceiverListDialog
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

@Composable
fun PlayerDialogsHost(
    operations: PlayerFileOperationsManager,
    visibilities: PlayerDialogVisibilities,
    currentFileName: String?
) {
    val showActions = visibilities.showActions
    val showDeleteConfirm = visibilities.showDeleteConfirm
    val showReceivers = visibilities.showReceivers
    val onActionsVisibilityChange = visibilities.onActionsVisibilityChange
    val onDeleteVisibilityChange = visibilities.onDeleteVisibilityChange
    val onReceiversVisibilityChange = visibilities.onReceiversVisibilityChange
    val allowedOperations by operations.allowedOperations.collectAsStateWithLifecycle()
    val receivers by operations.sendToReceivers.collectAsStateWithLifecycle()
    val run by operations.operationRun.collectAsStateWithLifecycle()

    val requestRename = rememberWearRenameInput { newName ->
        operations.runOperation(WearFileOperation.Rename(newName))
    }

    if (showActions) {
        FileActionsDialog(
            state = FileActionsDialogState(
                selectedCount = 1,
                totalCount = 1,
                allowedOperations = allowedOperations
            ),
            callbacks = FileActionsCallbacks(
                onSelectAllRequested = {},
                onSendToRequested = {
                    onActionsVisibilityChange(false)
                    onReceiversVisibilityChange(true)
                },
                onSendToPhone = {
                    onActionsVisibilityChange(false)
                    operations.runOperation(WearFileOperation.SendToPhone)
                },
                onMoveToPhone = {
                    onActionsVisibilityChange(false)
                    operations.runOperation(WearFileOperation.MoveToPhone)
                },
                onRenameRequested = {
                    onActionsVisibilityChange(false)
                    requestRename(currentFileName)
                },
                onDeleteRequested = {
                    onActionsVisibilityChange(false)
                    onDeleteVisibilityChange(true)
                },
                onDismiss = { onActionsVisibilityChange(false) }
            )
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
