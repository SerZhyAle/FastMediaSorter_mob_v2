package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.ui.dialog.FileOperationDestinationDialog
import com.sza.fastmediasorter.ui.player.helpers.FileCopyProgressDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val callbacks: FileOperationCallbacks,
    private val dirOperationHandler: com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler? = null
) {
    
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
                        is FileOperationResult.Success -> Toast.makeText(context, context.getString(R.string.moved_n_files, result.processedCount), Toast.LENGTH_SHORT).show()
                        is FileOperationResult.PartialSuccess -> Toast.makeText(context, context.getString(R.string.moved_n_files, result.processedCount), Toast.LENGTH_SHORT).show()
                        is FileOperationResult.Failure -> Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
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
                Toast.makeText(context, context.getString(R.string.move_failed, e.message), Toast.LENGTH_LONG).show()
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
            val folderName = destinationPath.substringAfterLast('/')
            Toast.makeText(context, context.getString(msgRes, folderName), Toast.LENGTH_LONG).show()
        }

        coroutineScope.launch {
            try {
                val destinationFolder = if (destinationPath.startsWith("smb://") ||
                    destinationPath.startsWith("sftp://") ||
                    destinationPath.startsWith("ftp://") ||
                    destinationPath.startsWith("cloud://")
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
                    }
                    is FileOperationResult.Failure -> {
                        Timber.e("executeOperationToPath: FAILURE - ${result.error}")
                        Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
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
                Toast.makeText(context, context.getString(R.string.operation_failed, e.message), Toast.LENGTH_LONG).show()
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

        // For network/cloud paths, create File with URI-compatible scheme
        val selectedFiles = fileOnlyPaths.map { path ->
            val size = mediaFilesMap[path]?.size ?: 0L
            if (path.startsWith("smb://") || path.startsWith("sftp://") || 
                path.startsWith("ftp://") || path.startsWith("cloud://")) {
                object : File(path) {
                    override fun getAbsolutePath(): String = path
                    override fun getPath(): String = path
                    override fun length(): Long = size
                }
            } else {
                File(path)
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
        
        val selectedFiles = fileOnlyPaths.map { path ->
            val size = mediaFilesMap[path]?.size ?: 0L
            if (path.startsWith("smb://") || path.startsWith("sftp://") || 
                path.startsWith("ftp://") || path.startsWith("cloud://")) {
                object : File(path) {
                    override fun getAbsolutePath(): String = path
                    override fun getPath(): String = path
                    override fun length(): Long = size
                }
            } else {
                File(path)
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
    ) {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(context, R.string.no_files_selected, Toast.LENGTH_SHORT).show()
            return
        }
        
        coroutineScope.launch {
            try {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show()
                
                val uris = mutableListOf<Uri>()
                
                for (mediaFile in selectedFiles) {
                    val fileToShare: File? = when (resource.type) {
                        ResourceType.LOCAL -> File(mediaFile.path)
                        ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP, ResourceType.CLOUD -> {
                            downloadNetworkFileToCacheWithProgress(mediaFile, resource)
                        }
                    }
                    
                    if (fileToShare != null && fileToShare.exists()) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            fileToShare
                        )
                        uris.add(uri)
                    }
                }
                
                if (uris.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val shareIntent = android.content.Intent().apply {
                    action = if (uris.size == 1) {
                        android.content.Intent.ACTION_SEND
                    } else {
                        android.content.Intent.ACTION_SEND_MULTIPLE
                    }
                    
                    if (uris.size == 1) {
                        putExtra(android.content.Intent.EXTRA_STREAM, uris[0])
                    } else {
                        putParcelableArrayListExtra(
                            android.content.Intent.EXTRA_STREAM,
                            ArrayList(uris)
                        )
                    }
                    
                    type = "*/*"
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                withContext(Dispatchers.Main) {
                    context.startActivity(
                        android.content.Intent.createChooser(shareIntent, context.getString(R.string.share))
                    )
                }
            } catch (_: CancellationException) {
                Timber.i("Share operation cancelled by user")
                return@launch
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to share files")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.toast_failed_to_share, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun downloadNetworkFileToCacheWithProgress(
        mediaFile: MediaFile,
        resource: MediaResource
    ): File? {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.msg_download_share, Toast.LENGTH_SHORT).show()
        }

        val cacheRoot = callbacks.getExternalCacheDir() ?: callbacks.getCacheDir() ?: return null
        val shareTempDir = File(cacheRoot, "share_temp")
        withContext(Dispatchers.IO) {
            if (!shareTempDir.exists()) {
                shareTempDir.mkdirs()
            }
            cleanupOldShareTempFiles(shareTempDir)
        }

        val tempFile = File(shareTempDir, mediaFile.name)
        withContext(Dispatchers.IO) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }

        val sourceFile = createNetworkAwareFile(mediaFile.path, mediaFile.name, mediaFile.size)
        val operation = FileOperation.Copy(
            sources = listOf(sourceFile),
            destination = shareTempDir,
            overwrite = true,
            sourceCredentialsId = resource.credentialsId
        )

        val totalBytes = mediaFile.size.coerceAtLeast(0L)
        val copyDeferred = coroutineScope.async(Dispatchers.IO) {
            fileOperationUseCase.execute(operation)
        }

        val progressDialog = if (context is Activity && !context.isFinishing && !context.isDestroyed) {
            FileCopyProgressDialog(
                context = context,
                fileName = mediaFile.name,
                onCancelRequested = {
                    copyDeferred.cancel(CancellationException("User cancelled network share copy"))
                }
            )
        } else {
            null
        }

        val monitorJob = coroutineScope.launch(Dispatchers.Main) {
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            progressDialog?.show()
            progressDialog?.showIndeterminate()

            while (copyDeferred.isActive) {
                val copiedBytes = tempFile.length().coerceAtLeast(0L)
                val now = System.currentTimeMillis()
                val elapsedMs = (now - lastTime).coerceAtLeast(1L)
                val bytesDelta = (copiedBytes - lastBytes).coerceAtLeast(0L)
                val speedBytesPerSec = (bytesDelta * 1000L) / elapsedMs

                progressDialog?.updateProgress(copiedBytes, totalBytes, speedBytesPerSec)

                lastTime = now
                lastBytes = copiedBytes
                delay(200)
            }
        }

        return try {
            when (val result = copyDeferred.await()) {
                is FileOperationResult.Success -> {
                    if (tempFile.exists()) {
                        tempFile
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                        }
                        null
                    }
                }
                is FileOperationResult.Failure -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.error_share_download_failed, result.error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    null
                }
                is FileOperationResult.AuthenticationRequired -> {
                    callbacks.onAuthRequest(result.provider)
                    null
                }
                else -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.error_share_unexpected, Toast.LENGTH_SHORT).show()
                    }
                    null
                }
            }
        } catch (_: CancellationException) {
            withContext(Dispatchers.IO) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.toast_copy_cancelled, Toast.LENGTH_SHORT).show()
            }
            throw CancellationException("Share copy cancelled after cleanup")
        } finally {
            monitorJob.cancel()
            withContext(Dispatchers.Main) {
                progressDialog?.dismiss()
            }
        }
    }

    private fun cleanupOldShareTempFiles(cacheDir: File) {
        val now = System.currentTimeMillis()
        cacheDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val age = now - file.lastModified()
            if (age > 60 * 60 * 1000L) {
                file.delete()
            }
        }
    }

    private fun createNetworkAwareFile(path: String, name: String?, size: Long): File {
        return if (path.startsWith("smb://") ||
            path.startsWith("sftp://") ||
            path.startsWith("ftp://") ||
            path.startsWith("cloud://")
        ) {
            object : File(path) {
                override fun getAbsolutePath(): String = path
                override fun getPath(): String = path
                override fun getName(): String = name ?: super.getName()
                override fun length(): Long = size
            }
        } else {
            File(path)
        }
    }
    
    private suspend fun downloadNetworkFileToCache(mediaFile: MediaFile, resource: MediaResource): File? {
        return withContext(Dispatchers.IO) {
            val cacheDir = callbacks.getExternalCacheDir() ?: callbacks.getCacheDir() ?: return@withContext null
            val fileName = mediaFile.name
            val tempFile = File(cacheDir, "share_$fileName")
            
            val downloadSuccess = when (resource.type) {
                ResourceType.SMB -> downloadSmbFile(mediaFile.path, resource, tempFile)
                ResourceType.SFTP -> downloadSftpFile(mediaFile.path, resource, tempFile)
                ResourceType.FTP -> downloadFtpFile(mediaFile.path, resource, tempFile)
                else -> false
            }
            
            if (downloadSuccess && tempFile.exists()) tempFile else null
        }
    }
    
    private suspend fun downloadSmbFile(path: String, resource: MediaResource, tempFile: File): Boolean {
        return try {
            if (resource.credentialsId == null) return false
            
            val credentials = credentialsRepository.getByCredentialId(resource.credentialsId) ?: return false
            val uri = PathUtils.safeParseUri(path)
            val host = uri.host ?: return false
            val pathSegments = uri.pathSegments
            if (pathSegments == null || pathSegments.size < 2) return false
            
            val shareName = pathSegments[0]
            val filePath = "/" + pathSegments.drop(1).joinToString("/")
            
            tempFile.outputStream().use { outputStream ->
                val result = smbClient.downloadFile(
                    SmbConnectionInfo(
                        server = host,
                        shareName = shareName,
                        username = credentials.username,
                        password = credentials.password,
                        domain = credentials.domain,
                        port = if (uri.port > 0) uri.port else 445
                    ),
                    remotePath = filePath,
                    localOutputStream = outputStream
                )
                result is SmbResult.Success
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download SMB file")
            false
        }
    }
    
    private suspend fun downloadSftpFile(path: String, resource: MediaResource, tempFile: File): Boolean {
        return try {
            if (resource.credentialsId == null) return false
            
            val credentials = credentialsRepository.getByCredentialId(resource.credentialsId) ?: return false
            val uri = PathUtils.safeParseUri(path)
            val host = uri.host ?: return false
            val port = if (uri.port > 0) uri.port else 22
            val sftpPath = uri.path ?: return false
            
            tempFile.outputStream().use { outputStream ->
                val connectionInfo = SftpClient.SftpConnectionInfo(
                    host = host,
                    port = port,
                    username = credentials.username,
                    password = credentials.password
                )
                val result = sftpClient.downloadFile(connectionInfo, sftpPath, outputStream)
                result.isSuccess
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download SFTP file")
            false
        }
    }
    
    private suspend fun downloadFtpFile(path: String, resource: MediaResource, tempFile: File): Boolean {
        return try {
            if (resource.credentialsId == null) return false
            
            val credentials = credentialsRepository.getByCredentialId(resource.credentialsId) ?: return false
            val uri = PathUtils.safeParseUri(path)
            val host = uri.host ?: return false
            val port = if (uri.port > 0) uri.port else 21
            val ftpPath = uri.path ?: return false
            
            ftpClient.connect(host, port, credentials.username, credentials.password)
            try {
                tempFile.outputStream().use { outputStream ->
                    val result = ftpClient.downloadFile(ftpPath, outputStream)
                    result.isSuccess
                }
            } finally {
                ftpClient.disconnect()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download FTP file")
            false
        }
    }
    
    fun cleanup() {
        // Cancel any pending operations if needed
    }
}
