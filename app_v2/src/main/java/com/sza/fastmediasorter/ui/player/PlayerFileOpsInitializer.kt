package com.sza.fastmediasorter.ui.player

import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.keybinding.helpers.KeybindingRowLabelFormatter
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperation
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperationEvent
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperationQueue
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.UUID

/** Builds [PlayerFileOperationQueue] + [FileOperationsHandler] + queue-event observer + folder picker + destination buttons for [PlayerActivity]. Extracted from [PlayerManagerInitializer.initFileOps] to keep the orchestrator under the 1000-LOC budget. */
internal class PlayerFileOpsInitializer(
    private val activity: PlayerActivity,
    private val recordQueuedOperationMutation: (PlayerFileOperation) -> Unit,
    private val buildRenamedPath: (String, String) -> String,
) {
    fun install() {
        activity.playerFileOperationQueue = PlayerFileOperationQueue(
            scope = activity.fileOpsAppScope,
            fileOperationUseCase = activity.viewModel.fileOperationUseCase,
            settingsRepository = activity.playerHostFactory.settingsRepository,
        )

        activity.fileOperationsHandler = FileOperationsHandler(
            context = activity,
            lifecycleScope = activity.lifecycleScope,
            appScope = activity.fileOpsAppScope,
            settingsRepository = activity.playerHostFactory.settingsRepository,
            fileOperationUseCase = activity.viewModel.fileOperationUseCase,
            playerFileOperationQueue = activity.playerFileOperationQueue,
            callback = buildFileOpsCallback(),
        )

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activity.playerFileOperationQueue.events.collect { handleQueueEvent(it) }
            }
        }

        activity.playerFolderPickerHandler = com.sza.fastmediasorter.ui.player.helpers.PlayerFolderPickerHandler(
            activity = activity,
            coroutineScope = activity.lifecycleScope,
            settingsRepository = activity.playerHostFactory.settingsRepository,
            fileOperationsHandler = activity.fileOperationsHandler,
            restrictedTreeTargetPolicy = activity.restrictedTreeTargetPolicy,
            onLaunchPicker = { uri -> activity.folderPickerLauncher.launch(uri) },
        )

        val slotLabelFormatter = KeybindingRowLabelFormatter(activity)
        activity.destinationButtonsManager = DestinationButtonsManager(
            root = activity.activityBinding.root,
            settingsRepository = activity.playerHostFactory.settingsRepository,
            getDestinationsUseCase = activity.viewModel.getDestinationsUseCase,
            lifecycleScope = activity.lifecycleScope,
            callback = buildDestinationButtonsCallback(),
            // TV remote / D-pad / hardware keyboard all number the slots; touch-only does not.
            shouldNumberSlots = { activity.shouldNumberOperationSlots() },
            // Reflect the key actually bound to the slot; null falls back to the position digit in refreshSlotBadges.
            slotKeyGlyph = { slot ->
                activity.keyBindingManager.triggersFor("sorting.op_slot_$slot")
                    .filterIsInstance<InputTrigger.Key>()
                    .firstOrNull()
                    ?.let { slotLabelFormatter.format(it) }
            },
        )
    }

    private fun buildFileOpsCallback() = object : FileOperationsHandler.FileOperationCallback {
        override fun onBeforeMove(movedFilePath: String) {
            if (movedFilePath != activity.viewModel.state.value.currentFile?.path) return
            activity.stopVideoPlayback()
            activity.viewModel.state.value.resource?.let { resource ->
                MediaFilesCacheManager.removeFile(resource.id, movedFilePath)
            }
            dropFromNavigationList(movedFilePath)
        }

        override fun onBeforeDelete(deletedFilePath: String) {
            if (deletedFilePath != activity.viewModel.state.value.currentFile?.path) return
            activity.stopVideoPlayback()
            activity.viewModel.state.value.resource?.let { resource ->
                MediaFilesCacheManager.removeFile(resource.id, deletedFilePath)
            }
            dropFromNavigationList(deletedFilePath)
            val nextPath = activity.viewModel.state.value.currentFile?.path
            if (nextPath != null && nextPath != deletedFilePath) {
                activity.viewModel.saveLastViewedFile(nextPath)
            }
        }

        override fun onCopySuccess(destination: com.sza.fastmediasorter.domain.model.MediaResource, goToNext: Boolean) {
            if (goToNext) activity.navigationManager.navigateNextAfterOperation("Copy success with goToNext=true")
        }

        override fun onCopyToPathSuccess(destinationPath: String, goToNext: Boolean) {
            if (goToNext) activity.navigationManager.navigateNextAfterOperation("CopyToPath success with goToNext=true")
        }

        override fun onOperationError(message: String, throwable: Throwable?) = activity.showError(message, throwable)

        override fun onAuthenticationRequired(provider: String, message: String) =
            activity.eventHandler.showCloudAuthenticationError(provider)

        override fun getCurrentFile(): com.sza.fastmediasorter.domain.model.MediaFile? =
            activity.viewModel.state.value.currentFile

        override fun getCurrentResource(): com.sza.fastmediasorter.domain.model.MediaResource? =
            activity.viewModel.state.value.resource
    }

    private fun buildDestinationButtonsCallback() = object : DestinationButtonsManager.DestinationButtonsCallback {
        override fun onCopyClicked(destination: com.sza.fastmediasorter.domain.model.MediaResource) =
            activity.fileOperationsHandler.performCopy(destination)
        override fun onMoveClicked(destination: com.sza.fastmediasorter.domain.model.MediaResource) =
            activity.fileOperationsHandler.performMove(destination)
        override fun onCustomPathPickerRequested(operationType: com.sza.fastmediasorter.domain.model.FileOperationType) {
            val credId = activity.viewModel.state.value.resource?.credentialsId
            activity.playerFolderPickerHandler.requestFolderPick(operationType, credId)
        }
        override fun getCurrentResourceId(): Long = activity.intent.getLongExtra("resourceId", -1)
        override fun onUpdateCommandAvailability() {
            activity.updateCommandAvailability(activity.viewModel.state.value)
        }
        override fun shouldShowDestinationPanels(): Boolean {
            val state = activity.viewModel.state.value
            // S0741: an active image-edit overlay (draw/crop) full-screens the media and
            // PlayerImmersiveModeManager already hid the panels - this async populate must not
            // re-show them over the overlay. imageEditMode is the same state immersive keys off.
            if (state.imageEditMode != com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.NONE) return false
            return state.showCommandPanel || state.currentFile?.type == MediaType.AUDIO
        }
    }

    /**
     * S1279: the file leaving the list IS the advance. Removing the entry at the current index
     * leaves that index pointing at the following file, which is why this replaces the
     * `navigateNextAfterOperation` call that used to sit here - keeping both would step twice and
     * skip a file after every sort action.
     */
    private fun dropFromNavigationList(sourcePath: String) {
        activity.viewModel.dropCurrentFileFromList(sourcePath)
    }

    /** Delete and both move variants take the source away; copy and rename leave it addressable. */
    private fun removesSource(op: PlayerFileOperation): Boolean =
        op is PlayerFileOperation.Delete ||
            op is PlayerFileOperation.MoveToResource ||
            op is PlayerFileOperation.MoveToPath

    private fun handleQueueEvent(event: PlayerFileOperationEvent) = when (event) {
        is PlayerFileOperationEvent.Enqueued -> Timber.i("PlayerFileOperationQueue: enqueued %s for %s", event.op.id, event.op.sourcePath)
        is PlayerFileOperationEvent.Started -> {
            Timber.i("PlayerFileOperationQueue: started %s for %s", event.op.id, event.op.sourcePath)
            showStartedToast(event.op)
        }
        is PlayerFileOperationEvent.Succeeded -> {
            Timber.i("PlayerFileOperationQueue: succeeded %s for %s (processed=%s)", event.op.id, event.op.sourcePath, event.processedCount)
            if (wasSourceRemoved(event)) {
                recordQueuedOperationMutation(event.op)
                showSuccessToast(event.op)
            }
            Unit
        }
        is PlayerFileOperationEvent.Failed -> onFailed(event)
        is PlayerFileOperationEvent.AuthRequired -> {
            Timber.i("PlayerFileOperationQueue: auth required for %s (%s)", event.provider, event.op.sourcePath)
            activity.eventHandler.showCloudAuthenticationError(event.provider)
        }
        is PlayerFileOperationEvent.PermissionRequired -> onPermissionRequired(event)
        PlayerFileOperationEvent.Drained -> {
            Timber.i("PlayerFileOperationQueue: drained")
            if (activity.viewModel.state.value.files.isEmpty() && !activity.isFinishing && !activity.isDestroyed) {
                activity.finish()
            }
            Unit
        }
    }

    /** A skipped move reports queue success but leaves its source file in place. */
    private fun wasSourceRemoved(event: PlayerFileOperationEvent.Succeeded): Boolean {
        val sourceLeavingOperation = removesSource(event.op)
        if (sourceLeavingOperation) {
            if (event.processedCount > 0) {
                activity.viewModel.confirmFileRemoved(event.op.sourcePath)
            } else {
                activity.viewModel.restoreDroppedFile(event.op.sourcePath)
            }
        }
        return !sourceLeavingOperation || event.processedCount > 0
    }

    private fun onFailed(event: PlayerFileOperationEvent.Failed) {
        // S1279: undo any optimistic removal first, before the early returns below - a
        // non-retryable failure still means the file is there and must stay navigable. A no-op for
        // operations that never removed anything (copy, rename).
        activity.viewModel.restoreDroppedFile(event.op.sourcePath)
        if (event.op is PlayerFileOperation.Rename) {
            val queuedNewPath = buildRenamedPath(event.op.sourcePath, event.op.newName)
            if (activity.viewModel.state.value.currentFile?.path == queuedNewPath) {
                activity.viewModel.updateRenamedFilePath(queuedNewPath, event.op.sourcePath)
            }
        }
        Timber.w("PlayerFileOperationQueue: failed %s for %s: %s", event.op.id, event.op.sourcePath, event.message)
        if (!event.retryable || activity.isFinishing || activity.isDestroyed) return
        // S0226 permission-denied note: when batch-delete is denied on Move, the upload already completed; show a specific message + no retry.
        val isMovePermissionDenied = event.message == "permission_denied" &&
            (event.op is PlayerFileOperation.MoveToResource || event.op is PlayerFileOperation.MoveToPath)
        val snackbarMessage = if (isMovePermissionDenied) {
            activity.getString(R.string.error_queued_move_permission_denied, event.op.displayName)
        } else {
            queuedFailureMessage(event.op)
        }
        val snackbar = Snackbar.make(activity.activityBinding.root, snackbarMessage, Snackbar.LENGTH_LONG)
        if (!isMovePermissionDenied) {
            snackbar.setAction(activity.getString(R.string.action_retry).uppercase()) {
                activity.playerFileOperationQueue.enqueue(cloneQueuedOperation(event.op))
            }
        }
        snackbar.show()
    }

    private fun onPermissionRequired(event: PlayerFileOperationEvent.PermissionRequired) {
        Timber.i("PlayerFileOperationQueue: permission required for %s", event.op.sourcePath)
        activity.lifecycleManager.storePendingBatchDeleteFilePath(event.op.sourcePath)
        activity.lifecycleManager.storePendingBatchDeleteOperation(event.op)
        try {
            activity.batchDeletePermissionLauncher.launch(
                androidx.activity.result.IntentSenderRequest.Builder(event.pendingIntent.intentSender).build()
            )
        } catch (e: Exception) {
            activity.lifecycleManager.storePendingBatchDeleteOperation(null)
            activity.playerFileOperationQueue.resumeAfterPermission(false, event.op)
            activity.showError(activity.getString(R.string.error_delete_failed), e)
        }
    }

    private fun showStartedToast(operation: PlayerFileOperation) {
        if (activity.isFinishing || activity.isDestroyed) return
        when (operation) {
            is PlayerFileOperation.MoveToResource ->
                Toast.makeText(activity, activity.getString(R.string.msg_move_started, operation.destination.name), Toast.LENGTH_LONG).show()
            is PlayerFileOperation.MoveToPath ->
                Toast.makeText(activity, activity.getString(R.string.msg_move_started, destinationLabel(operation.destinationPath)), Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    private fun showSuccessToast(operation: PlayerFileOperation) {
        if (activity.isFinishing || activity.isDestroyed) return
        when (operation) {
            is PlayerFileOperation.MoveToResource ->
                Toast.makeText(activity, activity.getString(R.string.msg_move_success, operation.destination.name), Toast.LENGTH_SHORT).show()
            is PlayerFileOperation.MoveToPath ->
                Toast.makeText(activity, activity.getString(R.string.msg_move_success, destinationLabel(operation.destinationPath)), Toast.LENGTH_SHORT).show()
            is PlayerFileOperation.Delete ->
                Toast.makeText(activity, R.string.msg_delete_success, Toast.LENGTH_SHORT).show()
            is PlayerFileOperation.Rename ->
                Toast.makeText(activity, activity.getString(R.string.renamed_n_files, 1), Toast.LENGTH_SHORT).show()
        }
    }

    private fun queuedFailureMessage(operation: PlayerFileOperation): String = when (operation) {
        is PlayerFileOperation.MoveToResource, is PlayerFileOperation.MoveToPath ->
            activity.getString(R.string.error_queued_operation_move, operation.displayName)
        is PlayerFileOperation.Delete -> activity.getString(R.string.error_queued_operation_delete, operation.displayName)
        is PlayerFileOperation.Rename -> activity.getString(R.string.error_queued_operation_rename, operation.displayName)
    }

    private fun cloneQueuedOperation(op: PlayerFileOperation): PlayerFileOperation = when (op) {
        is PlayerFileOperation.MoveToResource -> op.copy(id = UUID.randomUUID().toString())
        is PlayerFileOperation.MoveToPath -> op.copy(id = UUID.randomUUID().toString())
        is PlayerFileOperation.Delete -> op.copy(id = UUID.randomUUID().toString())
        is PlayerFileOperation.Rename -> op.copy(id = UUID.randomUUID().toString())
    }

    private fun destinationLabel(destinationPath: String): String {
        val folderName = File(destinationPath).name
        return folderName.ifBlank { destinationPath }
    }
}
