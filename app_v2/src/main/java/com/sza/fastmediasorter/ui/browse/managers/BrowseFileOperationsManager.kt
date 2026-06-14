package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.CloudFileHandle
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.utils.SafHelper
import com.sza.fastmediasorter.ui.dialog.FileOperationDestinationDialog
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
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val callbacks: FileOperationCallbacks,
    private val dirOperationHandler: com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler? = null
) {
    private val shareOperationsHelper = BrowseShareOperationsHelper(
        context = context,
        coroutineScope = coroutineScope,
        fileOperationUseCase = fileOperationUseCase,
        callbacks = callbacks,
        showFailureError = ::showFailureError,
        showUnexpectedError = ::showUnexpectedError
    )

    
    private var pendingMoveOperation: PendingMoveOperation? = null
    
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
        /** Called after a successful Move or Copy operation. [count] = number of processed files. */
        fun onSortOperationSuccess(count: Int) {}
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
        
        val mediaFilesMap = mediaFiles.associateBy { it.path }
        val selectedSet = selectedPaths.toSet()

        // Partition into directory items and regular file items
        val (dirItems, fileItems) = mediaFiles
            .filter { it.path in selectedSet }
            .partition { it.isDirectory }
        val unresolvedPaths = selectedSet - mediaFiles.map { it.path }.toSet()
        val fileOnlyPaths = fileItems.map { it.path } + unresolvedPaths

        // Capture destination for directory operations
        var capturedDestination: MediaResource? = null

        // For network/cloud paths, create File with URI-compatible scheme.
        // S0266: cloud paths build CloudFileHandle carrying the display-name from MediaFile.name.
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

        val currentBrowsePath = selectedPaths.firstOrNull()?.let { firstPath ->
            val lastSlashIndex = firstPath.lastIndexOf('/')
            if (lastSlashIndex > 0) firstPath.substring(0, lastSlashIndex + 1) else null
        }

        val dialog = FileOperationDestinationDialog(
            context = context,
            operationType = FileOperationType.COPY,
            sourceFiles = selectedFiles,
            sourceFolderName = resource.name,
            currentResourceId = resource.id,
            currentBrowsePath = currentBrowsePath,
            sourceCredentialsId = resource.credentialsId,
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            overwriteFiles = false,
            showDetailedErrors = settings.showDetailedErrors,
            onDestinationSelected = { destination -> capturedDestination = destination },
            onComplete = { undoOp ->
                undoOp?.let { callbacks.saveUndoOperation(it) }
                callbacks.clearSelection()
                // Copy directory items to the captured destination
                if (dirItems.isNotEmpty() && dirOperationHandler != null) {
                    val destPath = capturedDestination?.path
                    if (destPath != null) {
                        coroutineScope.launch {
                            var succeeded = 0
                            var failed = 0
                            var crossProtocol = false
                            for (dir in dirItems) {
                                dirOperationHandler.executeCopyDirectory(dir.path, destPath)
                                    .onSuccess { succeeded++ }
                                    .onFailure { e ->
                                        Timber.e(e, "showCopyDialog: dir copy failed for ${dir.path}")
                                        if (e is UnsupportedOperationException) crossProtocol = true
                                        failed++
                                    }
                            }
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (failed == 0) {
                                    callbacks.onShowMessage(context.getString(R.string.operation_completed))
                                } else if (crossProtocol) {
                                    callbacks.onShowError(context.getString(R.string.error_cross_protocol_dir_not_supported))
                                } else {
                                    callbacks.onShowError(
                                        context.getString(R.string.error_some_operations_failed, failed, succeeded + failed)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            onAuthRequest = { provider ->
                callbacks.onAuthRequest(provider)
            },
            onPermissionRequired = { pendingIntent, _ ->
                // Copy operation shouldn't need delete permission, but handle it anyway
                callbacks.onPermissionRequired(pendingIntent)
            },
            onSelectFolderClicked = { opType, files, credId ->
                callbacks.onFolderPickerRequested(opType, files, credId, resource.type, resource, dirItems)
            }
        )
        dialog.show()
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
        
        // Check Safe Mode for move confirmation
        val shouldConfirmMove = settings.enableSafeMode && settings.confirmMove
        
        if (shouldConfirmMove) {
            // Show confirmation dialog first
            AlertDialog.Builder(context)
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
    
    private fun showMoveDialogInternal(
        selectedPaths: List<String>,
        mediaFiles: List<MediaFile>,
        resource: MediaResource,
        settings: AppSettings
    ) {
        val mediaFilesMap = mediaFiles.associateBy { it.path }
        val selectedSet = selectedPaths.toSet()

        // Partition into directory items and regular file items
        val (dirItems, fileItems) = mediaFiles
            .filter { it.path in selectedSet }
            .partition { it.isDirectory }
        val unresolvedPaths = selectedSet - mediaFiles.map { it.path }.toSet()
        val fileOnlyPaths = fileItems.map { it.path } + unresolvedPaths

        // Capture destination for directory move operations
        var capturedDestination: MediaResource? = null
        
        // S0266: cloud paths build CloudFileHandle carrying the display-name from MediaFile.name.
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

        val currentBrowsePath = selectedPaths.firstOrNull()?.let { firstPath ->
            val lastSlashIndex = firstPath.lastIndexOf('/')
            if (lastSlashIndex > 0) firstPath.substring(0, lastSlashIndex + 1) else null
        }

        val dialog = FileOperationDestinationDialog(
            context = context,
            operationType = FileOperationType.MOVE,
            sourceFiles = selectedFiles,
            sourceFolderName = resource.name,
            currentResourceId = resource.id,
            currentBrowsePath = currentBrowsePath,
            sourceCredentialsId = resource.credentialsId,
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            overwriteFiles = settings.overwriteOnMove,
            showDetailedErrors = settings.showDetailedErrors,
            onDestinationSelected = { destination -> capturedDestination = destination },
            onComplete = { undoOp ->
                // Clear pending operation on success
                pendingMoveOperation = null
                undoOp?.let { callbacks.saveUndoOperation(it) }
                callbacks.clearSelection()
                // Move directory items to the captured destination
                if (dirItems.isNotEmpty() && dirOperationHandler != null) {
                    val destPath = capturedDestination?.path
                    if (destPath != null) {
                        coroutineScope.launch {
                            var succeeded = 0
                            var failed = 0
                            var crossProtocol = false
                            for (dir in dirItems) {
                                dirOperationHandler.executeMoveDirectory(dir.path, destPath)
                                    .onSuccess { succeeded++ }
                                    .onFailure { e ->
                                        Timber.e(e, "showMoveDialogInternal: dir move failed for ${dir.path}")
                                        if (e is UnsupportedOperationException) crossProtocol = true
                                        failed++
                                    }
                            }
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (failed == 0) {
                                    callbacks.onShowMessage(context.getString(R.string.operation_completed))
                                } else if (crossProtocol) {
                                    callbacks.onShowError(context.getString(R.string.error_cross_protocol_dir_not_supported))
                                } else {
                                    callbacks.onShowError(
                                        context.getString(R.string.error_some_operations_failed, failed, succeeded + failed)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            onAuthRequest = { provider ->
                callbacks.onAuthRequest(provider)
            },
            onPermissionRequired = { pendingIntent, destination ->
                // Save pending operation info for retry after permission grant
                // destination is the user-selected destination resource
                if (destination != null) {
                    Timber.i("Move operation requires permission, saving pending state for retry to ${destination.name}")
                    pendingMoveOperation = PendingMoveOperation(selectedPaths, mediaFiles, resource, destination, settings)
                } else {
                    Timber.w("Move operation requires permission but destination is null")
                    pendingMoveOperation = null
                }
                callbacks.onPermissionRequired(pendingIntent)
            },
            onSelectFolderClicked = { opType, files, credId ->
                callbacks.onFolderPickerRequested(opType, files, credId, resource.type, resource, dirItems)
            }
        )
        dialog.show()
    }
    
    fun shareSelectedFiles(
        selectedFiles: List<MediaFile>,
        resource: MediaResource
    ) = shareOperationsHelper.shareSelectedFiles(selectedFiles, resource)

    // S0303: send selected file(s) to an installed Telegram client.
    fun sendSelectedFilesToTelegram(
        selectedFiles: List<MediaFile>,
        resource: MediaResource
    ) = shareOperationsHelper.sendSelectedFilesToTelegram(selectedFiles, resource)

    fun cleanup() {
        // Cancel any pending operations if needed
    }
}
