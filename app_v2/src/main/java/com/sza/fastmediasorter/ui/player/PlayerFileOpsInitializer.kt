package com.sza.fastmediasorter.ui.player

import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.MediaType
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
            settingsRepository = activity.settingsRepository,
        )

        activity.fileOperationsHandler = FileOperationsHandler(
            context = activity,
            lifecycleScope = activity.lifecycleScope,
            appScope = activity.fileOpsAppScope,
            settingsRepository = activity.settingsRepository,
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
            settingsRepository = activity.settingsRepository,
            fileOperationsHandler = activity.fileOperationsHandler,
            onLaunchPicker = { uri -> activity.folderPickerLauncher.launch(uri) },
        )

        activity.destinationButtonsManager = DestinationButtonsManager(
            binding = activity.activityBinding,
            settingsRepository = activity.settingsRepository,
            getDestinationsUseCase = activity.viewModel.getDestinationsUseCase,
            lifecycleScope = activity.lifecycleScope,
            callback = buildDestinationButtonsCallback(),
        )
    }

    private fun buildFileOpsCallback() = object : FileOperationsHandler.FileOperationCallback {
        override fun onBeforeMove(movedFilePath: String) {
            if (movedFilePath != activity.viewModel.state.value.currentFile?.path) return
            activity.stopVideoPlayback()
            activity.viewModel.state.value.resource?.let { resource ->
                MediaFilesCacheManager.removeFile(resource.id, movedFilePath)
            }
            activity.navigationManager.navigateNextAfterOperation("Pre-move: stop and optimistic advance")
        }

        override fun onBeforeDelete(deletedFilePath: String) {
            if (deletedFilePath != activity.viewModel.state.value.currentFile?.path) return
            activity.stopVideoPlayback()
            activity.viewModel.state.value.resource?.let { resource ->
                MediaFilesCacheManager.removeFile(resource.id, deletedFilePath)
            }
            activity.navigationManager.navigateNextAfterOperation("Pre-delete: stop and optimistic advance")
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
        override fun isCommandPanelVisible(): Boolean {
            val state = activity.viewModel.state.value
            return state.showCommandPanel || state.currentFile?.type == MediaType.AUDIO
        }
    }

    private fun handleQueueEvent(event: PlayerFileOperationEvent) = when (event) {
        is PlayerFileOperationEvent.Enqueued -> Timber.i("PlayerFileOperationQueue: enqueued %s for %s", event.op.id, event.op.sourcePath)
        is PlayerFileOperationEvent.Started -> {
            Timber.i("PlayerFileOperationQueue: started %s for %s", event.op.id, event.op.sourcePath)
            showStartedToast(event.op)
        }
        is PlayerFileOperationEvent.Succeeded -> {
            Timber.i("PlayerFileOperationQueue: succeeded %s for %s (processed=%s)", event.op.id, event.op.sourcePath, event.processedCount)
            recordQueuedOperationMutation(event.op)
            showSuccessToast(event.op)
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

    private fun onFailed(event: PlayerFileOperationEvent.Failed) {
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
