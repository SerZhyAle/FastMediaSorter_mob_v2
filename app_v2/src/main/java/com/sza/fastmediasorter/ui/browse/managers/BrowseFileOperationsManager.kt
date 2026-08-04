package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.CloudFileHandle
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferProgressSnapshot
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferRequest
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferSource
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferTerminalEvent
import com.sza.fastmediasorter.ui.browse.transfer.transferOverallPercent
import com.sza.fastmediasorter.utils.SafHelper
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.ui.dialog.FileOperationDestinationDialog
import com.sza.fastmediasorter.ui.dialog.FileOperationProgressDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/** Pending move operation for retry after permission grant. Includes pre-selected destination. */
data class PendingMoveOperation(
    val selectedPaths: List<String>,
    val mediaFiles: List<MediaFile>,
    val sourceResource: MediaResource,
    val destinationResource: MediaResource,
    val settings: AppSettings
)

/** Manages copy/move/delete/share operations in BrowseActivity. */
class BrowseFileOperationsManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val lifecycleOwner: LifecycleOwner,
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val browseTransferCoordinator: BrowseFileTransferCoordinator,
    private val sendToMenuManager: com.sza.fastmediasorter.ui.share.SendToMenuManager,
    private val callbacks: FileOperationCallbacks,
    private val dirOperationHandler: com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler? = null
) {
    private val shareOperationsHelper = BrowseShareOperationsHelper(
        context = context,
        coroutineScope = coroutineScope,
        fileOperationUseCase = fileOperationUseCase,
        sendToMenuManager = sendToMenuManager,
        callbacks = callbacks,
        showFailureError = ::showFailureError,
        showUnexpectedError = ::showUnexpectedError
    )

    
    private var pendingMoveOperation: PendingMoveOperation? = null
    private var activeTransferDialog: FileOperationProgressDialog? = null
    private var modalDetachedByUser: Boolean = false
    private var lastAutoAttachPath: String? = null

    // S1227: the running transfer's type and latest snapshot, mirrored here so re-opening the modal
    // and rendering the strip never re-read the request store from the main thread (see S1230).
    private var activeOperationType: FileOperationType? = null
    private var lastProgressSnapshot: BrowseFileTransferProgressSnapshot? = null
    private var lastIndicatorLabel: String? = null
    
    interface FileOperationCallbacks {
        fun onOperationCompleted()
        fun saveUndoOperation(undoOp: UndoOperation)
        fun clearSelection()
        fun getCacheDir(): File?
        fun getExternalCacheDir(): File?
        fun onAuthRequest(provider: String)
        fun onPermissionRequired(pendingIntent: android.app.PendingIntent)
        fun onShowMessage(message: String)
        fun onShowError(message: String, details: String? = null)
        fun getCurrentResource(): MediaResource?
        fun getCurrentBrowsePath(): String?
        fun navigateToFolder(path: String)
        /** Called after a successful Move or Copy operation. [count] = number of processed files. */
        fun onSortOperationSuccess(count: Int) {}

        /**
         * S1227: shows or updates the non-modal strip for a transfer whose progress dialog the owner
         * sent to the background; [label] null hides it. Emitted only while the modal is detached -
         * with the modal on screen the strip would duplicate it.
         */
        fun onBackgroundTransferProgress(label: String?) {}

        /** Called when user clicks "Select folder" in the destination dialog. */
        fun onFolderPickerRequested(
            operationType: FileOperationType,
            sourceFiles: List<File>,
            sourceCredentialsId: String?,
            resourceType: com.sza.fastmediasorter.domain.model.ResourceType,
            resource: MediaResource,
            dirItems: List<MediaFile> = emptyList()
        )
    }

    init {
        observeBackgroundTransfers()
    }

    // Use formatArgs (the per-file reason text) directly as dialog details; fall back to errorRes-formatted
    // string only when no formatArgs are available. This avoids the redundant localized wrapper prefix.
    private fun showFailureError(messageRes: Int, result: FileOperationResult.Failure) {
        val details = result.formatArgs
            .firstOrNull()
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: result.errorRes?.let { context.getString(it, *result.formatArgs.toTypedArray()) }
        callbacks.onShowError(context.getString(messageRes), details)
    }

    private fun showUnexpectedError(messageRes: Int) {
        callbacks.onShowError(
            context.getString(messageRes),
            context.getString(R.string.error_reason_unknown)
        )
    }

    private fun destinationLabel(destinationPath: String): String {
        if (destinationPath.startsWith("content:/")) {
            val normalized = SafHelper.normalizeContentUri(destinationPath)
            val treeName = SafHelper.getTreeRoot(context, normalized)?.name
            if (!treeName.isNullOrBlank()) {
                return treeName
            }
            val resolved = runCatching {
                com.sza.fastmediasorter.core.util.UriPathResolver.getPath(context, android.net.Uri.parse(normalized))
            }.getOrNull()
            return resolved?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: normalized
        }
        return destinationPath.substringAfterLast('/').ifBlank { destinationPath }
    }

    private fun observeBackgroundTransfers() {
        lifecycleOwner.collectOnLifecycle(browseTransferCoordinator.terminalEvents) { event ->
            if (!browseTransferCoordinator.isTerminalHandled(event.workId)) {
                handleTerminalEvent(event, clearStoredMirror = true)
            }
        }

        lifecycleOwner.collectOnLifecycle(browseTransferCoordinator.activeTransferFlow()) { state ->
            if (state.isActive) {
                state.request?.let {
                    maybeReattachToBrowseFolder(it)
                    activeOperationType = it.operationType
                }
                if (activeTransferDialog == null && !modalDetachedByUser) {
                    state.request?.let { showTransferDialog(it.operationType, showImmediately = true) }
                }
                state.progress?.let {
                    lastProgressSnapshot = it
                    activeTransferDialog?.updateSnapshot(it)
                }
                publishDetachedIndicator()
                return@collectOnLifecycle
            }

            if (state.isTerminal && state.workId != null && !browseTransferCoordinator.isTerminalHandled(state.workId)) {
                val stored = browseTransferCoordinator.consumeStoredTerminalEvent()
                val terminalEvent = stored ?: state.terminalFallback
                if (terminalEvent != null) {
                    handleTerminalEvent(terminalEvent, clearStoredMirror = false)
                }
            }

            if (!state.isActive) {
                dismissTransferDialog()
            }
        }
    }

    private fun maybeReattachToBrowseFolder(request: BrowseFileTransferRequest) {
        val currentResource = callbacks.getCurrentResource() ?: return
        val targetPath = request.currentBrowsePath ?: return
        if (currentResource.id != request.sourceResourceId) return
        if (callbacks.getCurrentBrowsePath() == targetPath) return
        if (lastAutoAttachPath == targetPath) return
        lastAutoAttachPath = targetPath
        callbacks.navigateToFolder(targetPath)
    }

    private fun showTransferDialog(
        operationType: FileOperationType,
        showImmediately: Boolean,
    ) {
        if (activeTransferDialog != null) return
        modalDetachedByUser = false
        activeTransferDialog = FileOperationProgressDialog.show(
            context = context,
            operationType = context.getString(progressTitleRes(operationType)),
            onCancel = {
                coroutineScope.launch { browseTransferCoordinator.cancelActiveTransfer() }
            },
            onBackground = {
                modalDetachedByUser = true
                // The dialog dismisses itself on this click; dropping our reference is what lets
                // reattachTransferDialog() past showTransferDialog's already-showing guard.
                activeTransferDialog = null
                callbacks.onShowMessage(context.getString(R.string.browse_transfer_sent_to_background))
                publishDetachedIndicator()
            },
            showImmediately = showImmediately,
        )
    }

    /**
     * S1227: brings back the modal the owner sent to the background, for the transfer running now.
     * [showTransferDialog] clears [modalDetachedByUser], so the choice does not leak into the next
     * operation.
     */
    fun reattachTransferDialog() {
        val operationType = activeOperationType
        Timber.d("S1227: strip tapped, activeType=%s", operationType)
        if (operationType == null) return
        hideTransferIndicator()
        showTransferDialog(operationType, showImmediately = true)
        lastProgressSnapshot?.let { activeTransferDialog?.updateSnapshot(it) }
    }

    /**
     * S1227: while the modal is detached the strip is the only in-app place the transfer is visible,
     * so it carries operation, percent and current file. Re-emitting only on a changed label keeps
     * byte-level progress ticks from re-laying out the strip on every emission.
     */
    private fun publishDetachedIndicator() {
        // A detached modal with no live operation type means the transfer is already gone, so both
        // conditions collapse into the same "nothing to show" branch.
        val operationType = activeOperationType
        if (!modalDetachedByUser || operationType == null) {
            hideTransferIndicator()
            return
        }
        val snapshot = lastProgressSnapshot
        val percent = snapshot?.let {
            transferOverallPercent(
                completedOperationBytes = it.completedOperationBytes,
                totalOperationBytes = it.totalOperationBytes,
                currentIndex = it.currentIndex,
                totalFiles = it.totalFiles,
            )
        } ?: 0
        val label = context.getString(
            R.string.browse_transfer_indicator,
            context.getString(progressTitleRes(operationType)),
            percent,
            snapshot?.currentFile?.substringAfterLast('/').orEmpty(),
        )
        if (label != lastIndicatorLabel) {
            lastIndicatorLabel = label
            Timber.d("S1227: strip shows '%s'", label)
            callbacks.onBackgroundTransferProgress(label)
        }
    }

    private fun hideTransferIndicator() {
        if (lastIndicatorLabel == null) return
        lastIndicatorLabel = null
        callbacks.onBackgroundTransferProgress(null)
    }

    private fun dismissTransferDialog() {
        // Runs on every non-active emission, so it is also where the strip and the mirrored transfer
        // state are released - both must clear even when no dialog is currently held.
        activeOperationType = null
        lastProgressSnapshot = null
        hideTransferIndicator()
        val dialog = activeTransferDialog ?: return
        activeTransferDialog = null
        runCatching { dialog.dismiss() }
    }

    private fun handleTerminalEvent(
        event: BrowseFileTransferTerminalEvent,
        clearStoredMirror: Boolean,
    ) {
        dismissTransferDialog()
        lastAutoAttachPath = null
        modalDetachedByUser = false
        if (clearStoredMirror) {
            browseTransferCoordinator.clearStoredTerminalEvent()
        }
        browseTransferCoordinator.markTerminalHandled(event.workId)
        browseTransferCoordinator.clearTerminalReplay()

        when (event) {
            is BrowseFileTransferTerminalEvent.Success -> {
                event.undoOperation?.let(callbacks::saveUndoOperation)
                callbacks.clearSelection()
                callbacks.onOperationCompleted()
                callbacks.onSortOperationSuccess(event.processedCount)
                callbacks.onShowMessage(context.getString(doneMessageRes(event.operationType), event.processedCount))
            }
            is BrowseFileTransferTerminalEvent.PartialSuccess -> {
                event.undoOperation?.let(callbacks::saveUndoOperation)
                callbacks.clearSelection()
                callbacks.onOperationCompleted()
                if (event.undoOperation != null) {
                    callbacks.onSortOperationSuccess(event.processedCount)
                }
                if (event.processedCount > 0) {
                    callbacks.onShowMessage(context.getString(doneMessageRes(event.operationType), event.processedCount))
                }
                callbacks.onShowError(
                    context.getString(R.string.error_some_operations_failed, event.failedCount, event.processedCount + event.failedCount),
                    event.details,
                )
            }
            is BrowseFileTransferTerminalEvent.Failure -> {
                val fallback = context.getString(failureMessageRes(event.operationType))
                callbacks.onShowError(event.message.ifBlank { fallback }, event.details)
            }
            is BrowseFileTransferTerminalEvent.AuthenticationRequired -> {
                callbacks.onAuthRequest(event.provider)
            }
            is BrowseFileTransferTerminalEvent.PermissionRequired -> {
                val pendingIntent = event.pendingIntent
                if (pendingIntent != null) {
                    callbacks.onPermissionRequired(pendingIntent)
                } else {
                    callbacks.onShowError(context.getString(R.string.permission_error_retry))
                }
            }
            is BrowseFileTransferTerminalEvent.Cancelled -> {
                // S1325: a cancelled tree leaves whatever was already written at the destination, so
                // the message says so - "cancelled" alone reads as "nothing happened".
                callbacks.onShowMessage(
                    context.getString(cancelledMessageRes(event.operationType)) + " " +
                        context.getString(R.string.browse_transfer_folder_partial_state)
                )
            }
        }
    }

    private fun progressTitleRes(operationType: FileOperationType): Int = when (operationType) {
        FileOperationType.MOVE -> R.string.moving_files
        FileOperationType.DELETE -> R.string.deleting_files
        else -> R.string.copying_files
    }

    private fun doneMessageRes(operationType: FileOperationType): Int = when (operationType) {
        FileOperationType.MOVE -> R.string.moved_n_files
        FileOperationType.DELETE -> R.string.deleted_n_files
        else -> R.string.copied_n_files
    }

    private fun failureMessageRes(operationType: FileOperationType): Int = when (operationType) {
        FileOperationType.MOVE -> R.string.move_failed
        FileOperationType.DELETE -> R.string.delete_failed
        else -> R.string.copy_failed
    }

    private fun cancelledMessageRes(operationType: FileOperationType): Int = when (operationType) {
        FileOperationType.MOVE -> R.string.toast_move_cancelled
        FileOperationType.DELETE -> R.string.toast_delete_cancelled
        else -> R.string.toast_copy_cancelled
    }
    
    fun hasPendingMoveOperation(): Boolean = pendingMoveOperation != null

    fun retryPendingMoveOperation() {
        val pending = pendingMoveOperation ?: return
        pendingMoveOperation = null
        executeMoveDirectly(pending)
    }

    private fun executeMoveDirectly(pending: PendingMoveOperation) {
        val mediaFilesMap = pending.mediaFiles.associateBy { it.path }
        val selectedSet = pending.selectedPaths.toSet()
        val (dirItems, fileItems) = pending.mediaFiles.filter { it.path in selectedSet }.partition { it.isDirectory }
        val unresolvedPaths = selectedSet - pending.mediaFiles.map { it.path }.toSet()
        val fileOnlyPaths = fileItems.map { it.path } + unresolvedPaths
        val selectedFiles = fileOnlyPaths.map { path ->
            val size = mediaFilesMap[path]?.size ?: 0L
            if (path.startsWith("smb://") || path.startsWith("sftp://") ||
                path.startsWith("ftp://") || path.startsWith("cloud://")
            ) {
                object : File(path) {
                    override fun getAbsolutePath(): String = path
                    override fun getPath(): String = path
                    override fun length(): Long = size
                }
            } else {
                File(path)
            }
        }

        Toast.makeText(context, context.getString(R.string.msg_move_started, pending.destinationResource.name), Toast.LENGTH_SHORT).show()

        coroutineScope.launch {
            try {
                if (selectedFiles.isNotEmpty()) {
                    val operation = FileOperation.Move(
                        sources = selectedFiles,
                        destination = File(pending.destinationResource.path),
                        overwrite = pending.settings.overwriteOnMove,
                        sourceCredentialsId = pending.sourceResource.credentialsId
                    )
                    when (val result = fileOperationUseCase.execute(operation)) {
                        is FileOperationResult.Success -> {
                            Toast.makeText(context, context.getString(R.string.moved_n_files, result.processedCount), Toast.LENGTH_SHORT).show()
                            callbacks.onSortOperationSuccess(result.processedCount)
                        }
                        is FileOperationResult.PartialSuccess -> {
                            Toast.makeText(context, context.getString(R.string.moved_n_files, result.processedCount), Toast.LENGTH_SHORT).show()
                            // Surface the first error so access-denied / partial failures are not silently hidden
                            if (result.errors.isNotEmpty()) callbacks.onShowError(result.errors.first())
                        }
                        is FileOperationResult.Failure -> showFailureError(R.string.move_failed, result)
                        is FileOperationResult.PermissionRequired -> Toast.makeText(context, R.string.permission_error_retry, Toast.LENGTH_LONG).show()
                        is FileOperationResult.AuthenticationRequired -> callbacks.onAuthRequest(result.provider)
                    }
                }
                if (dirItems.isNotEmpty() && dirOperationHandler != null) {
                    val destPath = pending.destinationResource.path
                    for (dir in dirItems) {
                        dirOperationHandler.executeMoveDirectory(dir.path, destPath)
                    }
                }
                callbacks.clearSelection()
                callbacks.onOperationCompleted()
            } catch (e: Exception) {
                Timber.e(e, "executeMoveDirectly: Exception during move")
                showUnexpectedError(R.string.move_failed)
            }
        }
    }
    
    /**
     * Clear any pending move operation (e.g., when permission is denied).
     */
    fun clearPendingMoveOperation() {
        pendingMoveOperation = null
    }

    /**
     * Execute a copy or move operation to a custom folder path selected via folder picker.
     * Called from BrowseActivity after the system/network/cloud picker returns a result.
     *
     * @param operationType COPY or MOVE
     * @param sourceFiles   The list of source files to operate on
     * @param destinationPath The absolute path (local) or protocol-prefixed path for the destination
     * @param sourceCredentialsId Credentials ID for the source resource (may be null for local)
     * @param overwriteFiles Whether to overwrite existing files at destination
     */
    fun executeOperationToPath(
        operationType: FileOperationType,
        sourceFiles: List<File>,
        destinationPath: String,
        sourceCredentialsId: String?,
        overwriteFiles: Boolean = false
    ) {
        Timber.i("executeOperationToPath: $operationType → $destinationPath (${sourceFiles.size} files)")

        // Show start toast for large operations
        val totalSize = sourceFiles.sumOf { runCatching { it.length() }.getOrDefault(0L) }
        if (totalSize > 1024 * 1024) {
            val msgRes = when (operationType) {
                FileOperationType.COPY -> R.string.msg_copy_started
                FileOperationType.MOVE -> R.string.msg_move_started
                else -> R.string.msg_copy_started
            }
            val folderName = destinationLabel(destinationPath)
            Toast.makeText(context, context.getString(msgRes, folderName), Toast.LENGTH_LONG).show()
        }

        coroutineScope.launch {
            try {
                val destinationFolder = if (destinationPath.startsWith("smb://") ||
                    destinationPath.startsWith("sftp://") ||
                    destinationPath.startsWith("ftp://") ||
                    destinationPath.startsWith("cloud://") ||
                    destinationPath.startsWith("content://")
                ) {
                    object : File(destinationPath) {
                        override fun getAbsolutePath(): String = destinationPath
                        override fun getPath(): String = destinationPath
                    }
                } else {
                    File(destinationPath)
                }

                val operation = when (operationType) {
                    FileOperationType.COPY -> FileOperation.Copy(
                        sources = sourceFiles,
                        destination = destinationFolder,
                        overwrite = overwriteFiles,
                        sourceCredentialsId = sourceCredentialsId
                    )
                    FileOperationType.MOVE -> FileOperation.Move(
                        sources = sourceFiles,
                        destination = destinationFolder,
                        overwrite = overwriteFiles,
                        sourceCredentialsId = sourceCredentialsId
                    )
                    else -> throw IllegalArgumentException("Unsupported operation: $operationType")
                }

                val result = withContext(Dispatchers.IO) { fileOperationUseCase.execute(operation) }

                when (result) {
                    is FileOperationResult.Success -> {
                        Timber.i("executeOperationToPath: SUCCESS - ${result.processedCount} files")
                        val msgRes = when (operationType) {
                            FileOperationType.COPY -> R.string.copied_n_files
                            FileOperationType.MOVE -> R.string.moved_n_files
                            else -> R.string.copied_n_files
                        }
                        Toast.makeText(context, context.getString(msgRes, result.processedCount), Toast.LENGTH_SHORT).show()
                        val undoOp = UndoOperation(
                            type = operationType,
                            sourceFiles = sourceFiles.map { it.absolutePath },
                            destinationFolder = destinationPath,
                            copiedFiles = result.copiedFilePaths,
                            oldNames = null,
                            timestamp = System.currentTimeMillis()
                        )
                        callbacks.saveUndoOperation(undoOp)
                        callbacks.clearSelection()
                        callbacks.onOperationCompleted()
                        callbacks.onSortOperationSuccess(result.processedCount)
                    }
                    is FileOperationResult.PartialSuccess -> {
                        Timber.w("executeOperationToPath: PARTIAL - ${result.processedCount} of ${result.processedCount + result.failedCount}")
                        val msgRes = when (operationType) {
                            FileOperationType.COPY -> R.string.copied_n_files
                            FileOperationType.MOVE -> R.string.moved_n_files
                            else -> R.string.copied_n_files
                        }
                        Toast.makeText(context, context.getString(msgRes, result.processedCount), Toast.LENGTH_SHORT).show()
                        callbacks.clearSelection()
                        callbacks.onOperationCompleted()
                        // Surface SFTP partial failure details (access-denied, copied-source-remains, etc.)
                        if (result.errors.isNotEmpty()) callbacks.onShowError(result.errors.first())
                    }
                    is FileOperationResult.Failure -> {
                        Timber.e("executeOperationToPath: FAILURE - ${result.error}")
                        val messageRes = when (operationType) {
                            FileOperationType.COPY -> R.string.copy_failed
                            FileOperationType.MOVE -> R.string.move_failed
                            else -> R.string.error_operation_failed
                        }
                        showFailureError(messageRes, result)
                    }
                    is FileOperationResult.AuthenticationRequired -> {
                        Timber.w("executeOperationToPath: Auth required for ${result.provider}")
                        callbacks.onAuthRequest(result.provider)
                    }
                    is FileOperationResult.PermissionRequired -> {
                        Timber.w("executeOperationToPath: Permission required")
                        callbacks.onPermissionRequired(result.pendingIntent)
                    }
                }
            } catch (e: CancellationException) {
                Timber.w("executeOperationToPath: cancelled")
            } catch (e: Exception) {
                Timber.e(e, "executeOperationToPath: exception")
                showUnexpectedError(R.string.error_operation_failed)
            }
        }
    }

    private fun buildTransferSources(
        selectedPaths: List<String>,
        mediaFiles: List<MediaFile>,
    ): List<BrowseFileTransferSource> {
        val mediaFilesMap = mediaFiles.associateBy { it.path }
        return selectedPaths.map { path ->
            val mediaFile = mediaFilesMap[path]
            BrowseFileTransferSource(
                path = path,
                displayName = mediaFile?.name ?: path.substringAfterLast('/'),
                size = mediaFile?.size ?: 0L,
                isDirectory = mediaFile?.isDirectory == true,
            )
        }
    }

    private fun buildTransferRequest(
        operationType: FileOperationType,
        selectedPaths: List<String>,
        mediaFiles: List<MediaFile>,
        resource: MediaResource,
        destination: MediaResource,
        overwriteFiles: Boolean,
    ): BrowseFileTransferRequest {
        val currentBrowsePath = selectedPaths.firstOrNull()?.let { firstPath ->
            val lastSlashIndex = firstPath.lastIndexOf('/')
            if (lastSlashIndex > 0) firstPath.substring(0, lastSlashIndex + 1) else null
        }
        return BrowseFileTransferRequest(
            operationType = operationType,
            sourceResourceId = resource.id,
            sourceResourceName = resource.name,
            sourceCredentialsId = resource.credentialsId,
            currentBrowsePath = currentBrowsePath,
            destinationPath = destination.path,
            destinationName = destination.name,
            overwriteFiles = overwriteFiles,
            sources = buildTransferSources(selectedPaths, mediaFiles),
        )
    }

    private fun reattachExistingTransfer() {
        modalDetachedByUser = false
        val request = browseTransferCoordinator.readActiveRequest() ?: return
        showTransferDialog(request.operationType, showImmediately = true)
    }

    fun requestTransferDialogReattach() {
        reattachExistingTransfer()
    }

    private fun startBackgroundTransfer(request: BrowseFileTransferRequest) {
        coroutineScope.launch {
            when (val result = browseTransferCoordinator.enqueueIfIdle(request)) {
                is BrowseFileTransferCoordinator.EnqueueResult.ActiveAlreadyRunning -> {
                    callbacks.onShowMessage(context.getString(R.string.browse_transfer_already_running))
                    reattachExistingTransfer()
                }
                is BrowseFileTransferCoordinator.EnqueueResult.Enqueued -> {
                    Timber.i("BrowseFileOperationsManager: background transfer enqueued workId=%s", result.workId)
                }
            }
        }
    }

    fun showCopyDialog(
        selectedPaths: List<String>,
        mediaFiles: List<MediaFile>,
        resource: MediaResource,
        settings: AppSettings
    ) {
        Timber.d("showCopyDialog: Triggered for ${selectedPaths.size} files, resource=${resource.name}")
        if (selectedPaths.isEmpty()) {
            Toast.makeText(context, R.string.no_files_selected, Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            if (browseTransferCoordinator.hasActiveTransfer()) {
                callbacks.onShowMessage(context.getString(R.string.browse_transfer_already_running))
                reattachExistingTransfer()
                return@launch
            }

            val mediaFilesMap = mediaFiles.associateBy { it.path }
            val selectedSet = selectedPaths.toSet()
            val (dirItems, fileItems) = mediaFiles
                .filter { it.path in selectedSet }
                .partition { it.isDirectory }
            val unresolvedPaths = selectedSet - mediaFiles.map { it.path }.toSet()
            val fileOnlyPaths = fileItems.map { it.path } + unresolvedPaths

            val selectedFiles = fileOnlyPaths.map { path ->
                val size = mediaFilesMap[path]?.size ?: 0L
                when {
                    path.startsWith("cloud://") -> CloudFileHandle(
                        cloudPath = path,
                        displayName = mediaFilesMap[path]?.name ?: path.substringAfterLast('/'),
                        size = size,
                    )
                    path.startsWith("smb://") || path.startsWith("sftp://") || path.startsWith("ftp://") -> {
                        object : File(path) {
                            override fun getAbsolutePath(): String = path
                            override fun getPath(): String = path
                            override fun length(): Long = size
                        }
                    }
                    else -> File(path)
                }
            }

            val dialog = FileOperationDestinationDialog(
                context = context,
                operationType = FileOperationType.COPY,
                sourceFiles = selectedFiles,
                sourceFolderName = resource.name,
                currentResourceId = resource.id,
                currentBrowsePath = callbacks.getCurrentBrowsePath(),
                sourceCredentialsId = resource.credentialsId,
                fileOperationUseCase = fileOperationUseCase,
                getDestinationsUseCase = getDestinationsUseCase,
                overwriteFiles = false,
                showDetailedErrors = settings.showDetailedErrors,
                onComplete = {},
                onSelectFolderClicked = { opType, files, credId ->
                    callbacks.onFolderPickerRequested(opType, files, credId, resource.type, resource, dirItems)
                },
                onOperationRequested = { destination ->
                    startBackgroundTransfer(
                        buildTransferRequest(
                            operationType = FileOperationType.COPY,
                            selectedPaths = selectedPaths,
                            mediaFiles = mediaFiles,
                            resource = resource,
                            destination = destination,
                            overwriteFiles = false,
                        )
                    )
                }
            )
            dialog.directoryCount = dirItems.size
            dialog.show()
        }
    }
    
    fun showMoveDialog(
        selectedPaths: List<String>,
        mediaFiles: List<MediaFile>,
        resource: MediaResource,
        settings: AppSettings
    ) {
        Timber.d("showMoveDialog: Triggered for ${selectedPaths.size} files, resource=${resource.name}")
        if (selectedPaths.isEmpty()) {
            Toast.makeText(context, R.string.no_files_selected, Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            if (browseTransferCoordinator.hasActiveTransfer()) {
                callbacks.onShowMessage(context.getString(R.string.browse_transfer_already_running))
                reattachExistingTransfer()
                return@launch
            }

            val shouldConfirmMove = settings.enableSafeMode && settings.confirmMove
            if (shouldConfirmMove) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.confirm_move_title)
                    .setMessage(context.getString(R.string.confirm_move_message, selectedPaths.size, resource.name))
                    .setPositiveButton(R.string.move) { _, _ ->
                        showMoveDialogInternal(selectedPaths, mediaFiles, resource, settings)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                showMoveDialogInternal(selectedPaths, mediaFiles, resource, settings)
            }
        }
    }
    
    private fun showMoveDialogInternal(
        selectedPaths: List<String>,
        mediaFiles: List<MediaFile>,
        resource: MediaResource,
        settings: AppSettings
    ) {
        val mediaFilesMap = mediaFiles.associateBy { it.path }
        val selectedSet = selectedPaths.toSet()
        val (dirItems, fileItems) = mediaFiles
            .filter { it.path in selectedSet }
            .partition { it.isDirectory }
        val unresolvedPaths = selectedSet - mediaFiles.map { it.path }.toSet()
        val fileOnlyPaths = fileItems.map { it.path } + unresolvedPaths

        val selectedFiles = fileOnlyPaths.map { path ->
            val size = mediaFilesMap[path]?.size ?: 0L
            when {
                path.startsWith("cloud://") -> CloudFileHandle(
                    cloudPath = path,
                    displayName = mediaFilesMap[path]?.name ?: path.substringAfterLast('/'),
                    size = size,
                )
                path.startsWith("smb://") || path.startsWith("sftp://") || path.startsWith("ftp://") -> {
                    object : File(path) {
                        override fun getAbsolutePath(): String = path
                        override fun getPath(): String = path
                        override fun length(): Long = size
                    }
                }
                else -> File(path)
            }
        }

        val dialog = FileOperationDestinationDialog(
            context = context,
            operationType = FileOperationType.MOVE,
            sourceFiles = selectedFiles,
            sourceFolderName = resource.name,
            currentResourceId = resource.id,
            currentBrowsePath = callbacks.getCurrentBrowsePath(),
            sourceCredentialsId = resource.credentialsId,
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            overwriteFiles = settings.overwriteOnMove,
            showDetailedErrors = settings.showDetailedErrors,
            onComplete = {},
            onSelectFolderClicked = { opType, files, credId ->
                callbacks.onFolderPickerRequested(opType, files, credId, resource.type, resource, dirItems)
            },
            onOperationRequested = { destination ->
                startBackgroundTransfer(
                    buildTransferRequest(
                        operationType = FileOperationType.MOVE,
                        selectedPaths = selectedPaths,
                        mediaFiles = mediaFiles,
                        resource = resource,
                        destination = destination,
                        overwriteFiles = settings.overwriteOnMove,
                    )
                )
            }
        )
        dialog.directoryCount = dirItems.size
        dialog.show()
    }
    
    // S0459 Phase 07: single outbound path - stages Uris then routes through the unified «Send to..» menu.
    fun sendFilesToMenu(
        selectedFiles: List<MediaFile>,
        resource: MediaResource,
        settings: AppSettings,
    ) = shareOperationsHelper.sendFilesToMenu(selectedFiles, resource, settings)

    fun cleanup() {
        dismissTransferDialog()
    }
}
