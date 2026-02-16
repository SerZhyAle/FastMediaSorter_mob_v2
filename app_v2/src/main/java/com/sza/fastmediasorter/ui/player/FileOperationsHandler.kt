package com.sza.fastmediasorter.ui.player

import android.app.PendingIntent
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.ui.player.helpers.FileCopyProgressDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Handles file operations (copy, move, delete, share) in PlayerActivity.
 * Manages network path handling, UseCase execution, and result callbacks.
 */
class FileOperationsHandler(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val fileOperationUseCase: FileOperationUseCase,
    private val callback: FileOperationCallback
) {
    interface FileOperationCallback {
        fun onCopySuccess(destination: MediaResource, goToNext: Boolean)
        fun onMoveSuccess(destination: MediaResource, movedFilePath: String, goToNext: Boolean)
        fun onDeleteSuccess(deletedFilePath: String)
        fun onOperationError(message: String, throwable: Throwable? = null)
        fun onAuthenticationRequired(provider: String, message: String)
        fun onBatchDeletePermissionRequired(pendingIntent: PendingIntent)
        fun getCurrentFile(): MediaFile?
        fun getCurrentResource(): MediaResource?
    }

    /**
     * Perform copy operation to destination resource.
     */
    fun performCopy(destination: MediaResource) {
        val currentFile = callback.getCurrentFile() ?: return
        
        // Show immediate feedback that operation started
        Toast.makeText(
            context,
            context.getString(com.sza.fastmediasorter.R.string.msg_copy_started, destination.name),
            Toast.LENGTH_LONG
        ).show()
        
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            
            try {
                val sourceFile = createNetworkAwareFile(currentFile.path, currentFile.name)
                val destFile = createNetworkAwareFile(destination.path, null)
                
                val operation = FileOperation.Copy(
                    sources = listOf(sourceFile),
                    destination = destFile,
                    overwrite = settings.overwriteOnCopy,
                    sourceCredentialsId = callback.getCurrentResource()?.credentialsId
                )
                
                val result = fileOperationUseCase.execute(operation)
                
                when (result) {
                    is FileOperationResult.Success -> {
                        Toast.makeText(context, context.getString(com.sza.fastmediasorter.R.string.msg_copy_success, destination.name), Toast.LENGTH_SHORT).show()
                        callback.onCopySuccess(destination, settings.goToNextAfterCopy)
                    }
                    is FileOperationResult.PartialSuccess -> {
                        val successCount = result.processedCount
                        Toast.makeText(context, context.getString(com.sza.fastmediasorter.R.string.msg_copy_success_count, successCount, destination.name), Toast.LENGTH_SHORT).show()
                        if (settings.goToNextAfterCopy) {
                            callback.onCopySuccess(destination, true)
                        }
                    }
                    is FileOperationResult.Failure -> {
                        val errorText = if (result.errorRes != null) {
                            context.getString(result.errorRes, *result.formatArgs.toTypedArray())
                        } else if (result.error.contains("already exists", ignoreCase = true)) {
                            context.getString(com.sza.fastmediasorter.R.string.error_file_exists_copy, currentFile.name, destination.name)
                        } else {
                            result.error
                        }
                        
                        // If using localized bulk error, use it directly. Otherwise wrap in generic "Copy failed"
                        val message = if (result.errorRes != null) errorText 
                                     else context.getString(com.sza.fastmediasorter.R.string.error_copy_failed, errorText)
                        
                        callback.onOperationError(message, null)
                    }
                    is FileOperationResult.AuthenticationRequired -> {
                        callback.onAuthenticationRequired(result.provider, result.message)
                    }
                    is FileOperationResult.PermissionRequired -> {
                        // This shouldn't happen for Copy operation, only Delete
                        callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_delete_unexpected))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: Copy operation failed")
                callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_copy_failed, e.message), e)
            }
        }
    }

    /**
     * Perform move operation to destination resource.
     */
    fun performMove(destination: MediaResource) {
        val currentFile = callback.getCurrentFile() ?: return
        
        // Show immediate feedback that operation started
        Toast.makeText(
            context,
            context.getString(com.sza.fastmediasorter.R.string.msg_move_started, destination.name),
            Toast.LENGTH_LONG
        ).show()
        
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            
            try {
                val sourceFile = createNetworkAwareFile(currentFile.path, currentFile.name)
                val destFile = createNetworkAwareFile(destination.path, null)
                
                val operation = FileOperation.Move(
                    sources = listOf(sourceFile),
                    destination = destFile,
                    overwrite = settings.overwriteOnMove,
                    sourceCredentialsId = callback.getCurrentResource()?.credentialsId
                )
                
                val result = fileOperationUseCase.execute(operation)
                
                when (result) {
                    is FileOperationResult.Success -> {
                        Toast.makeText(context, context.getString(com.sza.fastmediasorter.R.string.msg_move_success, destination.name), Toast.LENGTH_SHORT).show()
                        callback.onMoveSuccess(destination, currentFile.path, true)
                    }
                    is FileOperationResult.PartialSuccess -> {
                        val successCount = result.processedCount
                        Toast.makeText(context, context.getString(com.sza.fastmediasorter.R.string.msg_move_success_count, successCount, destination.name), Toast.LENGTH_SHORT).show()
                        callback.onMoveSuccess(destination, currentFile.path, true)
                    }
                    is FileOperationResult.Failure -> {
                        val errorText = if (result.errorRes != null) {
                            context.getString(result.errorRes, *result.formatArgs.toTypedArray())
                        } else if (result.error.contains("already exists", ignoreCase = true)) {
                            context.getString(com.sza.fastmediasorter.R.string.error_file_exists_move, currentFile.name, destination.name)
                        } else {
                            result.error
                        }
                        
                        // If using localized bulk error, use it directly. Otherwise wrap in generic "Move failed"
                        val message = if (result.errorRes != null) errorText 
                                     else context.getString(com.sza.fastmediasorter.R.string.error_move_failed, errorText)
                                     
                        callback.onOperationError(message, null)
                    }
                    is FileOperationResult.AuthenticationRequired -> {
                        callback.onAuthenticationRequired(result.provider, result.message)
                    }
                    is FileOperationResult.PermissionRequired -> {
                        // Move operation requires permission to delete source after copy
                        callback.onBatchDeletePermissionRequired(result.pendingIntent)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: Move operation failed")
                callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_move_failed, e.message), e)
            }
        }
    }

    /**
     * Delete current file with confirmation.
     */
    fun performDelete() {
        val currentFile = callback.getCurrentFile()
        if (currentFile == null) {
            Timber.e("FileOperationsHandler.performDelete: Current file is null!")
            callback.onOperationError("No file selected for deletion")
            return
        }
        
        Timber.d("FileOperationsHandler.performDelete: Starting delete for ${currentFile.path}")
        
        lifecycleScope.launch {
            try {
                val sourceFile = createNetworkAwareFile(currentFile.path, currentFile.name)
                
                Timber.d("FileOperationsHandler.performDelete: Source file created: ${sourceFile.path}")
                
                // Determine if soft-delete is possible (only for local files, not SAF/network/cloud)
                val canUseSoftDelete = !currentFile.path.startsWith("content:/") && 
                                      !currentFile.path.startsWith("smb://") && 
                                      !currentFile.path.startsWith("sftp://") && 
                                      !currentFile.path.startsWith("ftp://") && 
                                      !currentFile.path.startsWith("cloud://")
                
                Timber.d("FileOperationsHandler.performDelete: canUseSoftDelete=$canUseSoftDelete")
                
                val operation = FileOperation.Delete(
                    files = listOf(sourceFile),
                    softDelete = canUseSoftDelete
                )
                
                Timber.d("FileOperationsHandler.performDelete: Executing delete operation...")
                val result = fileOperationUseCase.execute(operation)
                Timber.d("FileOperationsHandler.performDelete: Result type: ${result::class.simpleName}")
                
                when (result) {
                    is FileOperationResult.Success -> {
                        Timber.i("FileOperationsHandler.performDelete: Delete SUCCESS")
                        Toast.makeText(context, context.getString(com.sza.fastmediasorter.R.string.msg_delete_success), Toast.LENGTH_SHORT).show()
                        callback.onDeleteSuccess(currentFile.path)
                    }
                    is FileOperationResult.PermissionRequired -> {
                        // Android 11+ batch delete - launch permission dialog
                        Timber.i("FileOperationsHandler.performDelete: Batch delete permission required, launching intent")
                        callback.onBatchDeletePermissionRequired(result.pendingIntent)
                    }
                    is FileOperationResult.Failure -> {
                        Timber.e("FileOperationsHandler.performDelete: Delete FAILED - ${result.error}")
                        val message = if (result.errorRes != null) {
                            context.getString(result.errorRes, *result.formatArgs.toTypedArray())
                        } else {
                            context.getString(com.sza.fastmediasorter.R.string.error_delete_failed, result.error)
                        }
                        callback.onOperationError(message, null)
                    }
                    else -> {
                        Timber.e("FileOperationsHandler.performDelete: Unexpected result type: ${result::class.simpleName}")
                        callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_delete_unexpected))
                    }
                }
            } catch (securityException: android.app.RecoverableSecurityException) {
                // Android 10+ requires user permission to delete from shared storage
                Timber.w("FileOperationsHandler: RecoverableSecurityException caught, requesting user permission")
                callback.onOperationError(
                    context.getString(com.sza.fastmediasorter.R.string.error_delete_permission_required),
                    securityException
                )
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: Delete operation failed with exception")
                callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_delete_failed, e.message), e)
            }
        }
    }

    /**
     * Share current file (local or download network file first).
     */
    fun performShare() {
        val currentFile = callback.getCurrentFile() ?: return
        val currentResource = callback.getCurrentResource()
        
        lifecycleScope.launch {
            try {
                // Check if file is network resource
                val isNetworkFile = currentFile.path.startsWith("smb://") ||
                                   currentFile.path.startsWith("sftp://") ||
                                   currentFile.path.startsWith("ftp://") ||
                                   currentFile.path.startsWith("cloud://")
                
                if (isNetworkFile) {
                    shareNetworkFileWithProgress(currentFile, currentResource)
                } else {
                    // Local file - share directly
                    shareLocalFile(File(currentFile.path))
                }
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: Share operation failed")
                callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_share_failed, e.message), e)
            }
        }
    }

    private suspend fun shareNetworkFileWithProgress(currentFile: MediaFile, currentResource: MediaResource?) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, com.sza.fastmediasorter.R.string.msg_download_share, Toast.LENGTH_SHORT).show()
        }

        val cacheDir = File(context.cacheDir, "share_temp")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        cleanupOldShareTempFiles(cacheDir)

        val tempFile = File(cacheDir, currentFile.name)
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val sourceFile = createNetworkAwareFile(currentFile.path, currentFile.name)
        val destDir = File(cacheDir.absolutePath)

        val operation = FileOperation.Copy(
            sources = listOf(sourceFile),
            destination = destDir,
            overwrite = true,
            sourceCredentialsId = currentResource?.credentialsId
        )

        val totalBytes = currentFile.size.coerceAtLeast(0L)

        val copyDeferred = lifecycleScope.async(Dispatchers.IO) {
            fileOperationUseCase.execute(operation)
        }

        val progressDialog = if (context is Activity && !context.isFinishing && !context.isDestroyed) {
            FileCopyProgressDialog(
                context = context,
                fileName = currentFile.name,
                onCancelRequested = {
                    copyDeferred.cancel(CancellationException("User cancelled network share copy"))
                }
            )
        } else {
            null
        }

        val monitorJob = lifecycleScope.launch {
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            if (progressDialog != null) {
                progressDialog.show()
                progressDialog.showIndeterminate()
            }

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

        try {
            when (val result = copyDeferred.await()) {
                is FileOperationResult.Success -> {
                    if (tempFile.exists()) {
                        shareLocalFile(tempFile)
                    } else {
                        callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_share_unexpected))
                    }
                }
                is FileOperationResult.Failure -> {
                    callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_share_download_failed, result.error), null)
                }
                else -> {
                    callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_share_unexpected))
                }
            }
        } catch (_: CancellationException) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, com.sza.fastmediasorter.R.string.toast_copy_cancelled, Toast.LENGTH_SHORT).show()
            }
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

    /**
     * Share local file using Android share intent.
     */
    private suspend fun shareLocalFile(file: File) {
        withContext(Dispatchers.Main) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = when {
                        file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true) -> "image/jpeg"
                        file.name.endsWith(".png", true) -> "image/png"
                        file.name.endsWith(".gif", true) -> "image/gif"
                        file.name.endsWith(".mp4", true) -> "video/mp4"
                        file.name.endsWith(".mp3", true) -> "audio/mpeg"
                        else -> "*/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                context.startActivity(Intent.createChooser(shareIntent, context.getString(com.sza.fastmediasorter.R.string.title_share_chooser)))
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: Failed to share local file")
                callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_share_failed, e.message), e)
            }
        }
    }

    /**
     * Create File object that preserves network paths (smb://, sftp://, ftp://, cloud://).
     */
    private fun createNetworkAwareFile(path: String, name: String?): File {
        return if (path.startsWith("smb://") || 
                   path.startsWith("sftp://") || 
                   path.startsWith("ftp://") ||
                   path.startsWith("cloud://")) {
            object : File(path) {
                override fun getAbsolutePath(): String = path
                override fun getPath(): String = path
                override fun getName(): String = name ?: super.getName()
            }
        } else {
            File(path)
        }
    }
}
