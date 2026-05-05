package com.sza.fastmediasorter.ui.player

import android.app.PendingIntent
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.DeletePathPolicy
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.ui.player.helpers.FileCopyProgressDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Handles file operations (copy, move, delete, share) in PlayerActivity.
 * Manages network path handling, UseCase execution, and result callbacks.
 */
class FileOperationsHandler(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val appScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val fileOperationUseCase: FileOperationUseCase,
    private val callback: FileOperationCallback
) {
    /**
     * App-context wrapper for Toasts that may fire after Activity is destroyed.
     * Activity context is invalid for view-system callbacks once the window is gone.
     */
    private val appCtx: Context get() = context.applicationContext

    @Volatile private var moveInProgress = false
    @Volatile private var deleteInProgress = false

    fun resetMoveInProgress() { moveInProgress = false }
    fun resetDeleteInProgress() { deleteInProgress = false }

    /**
     * True if `context` is an Activity that is finishing or destroyed.
     * Used to skip UI callbacks (Toasts, dialogs, navigation) on dead Activities
     * when copy/move complete on the long-lived appScope.
     */
    private fun isActivityGone(): Boolean {
        val act = context as? Activity ?: return false
        return act.isFinishing || act.isDestroyed
    }
    interface FileOperationCallback {
        fun onBeforeMove(movedFilePath: String)
        fun onBeforeDelete(deletedFilePath: String)
        fun onCopySuccess(destination: MediaResource, goToNext: Boolean)
        fun onMoveSuccess(destination: MediaResource, movedFilePath: String, goToNext: Boolean)
        fun onCopyToPathSuccess(destinationPath: String, goToNext: Boolean)
        fun onMoveToPathSuccess(destinationPath: String, movedFilePath: String, goToNext: Boolean)
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

        // Runs on appScope so the copy survives PlayerActivity destruction.
        // UI callbacks (Toast, navigation) are gated by isActivityGone() — when
        // the Activity is gone the user already moved on, so suppress the noise.
        appScope.launch {
            val destinationReachabilityError = checkSmbDestinationReachability(destination)
            if (destinationReachabilityError != null) {
                if (!isActivityGone()) {
                    withContext(Dispatchers.Main) {
                        callback.onOperationError(destinationReachabilityError, null)
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (!isActivityGone()) {
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(com.sza.fastmediasorter.R.string.msg_copy_started, destination.name),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

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

                withContext(Dispatchers.Main) {
                    if (isActivityGone()) {
                        Timber.i("FileOperationsHandler: copy completed after Activity destroyed — skipping UI callbacks (result=${result::class.simpleName})")
                        return@withContext
                    }
                    when (result) {
                        is FileOperationResult.Success -> {
                            Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_copy_success, destination.name), Toast.LENGTH_SHORT).show()
                            callback.onCopySuccess(destination, settings.goToNextAfterCopy)
                        }
                        is FileOperationResult.PartialSuccess -> {
                            val successCount = result.processedCount
                            Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_copy_success_count, successCount, destination.name), Toast.LENGTH_SHORT).show()
                            if (settings.goToNextAfterCopy) {
                                callback.onCopySuccess(destination, true)
                            }
                        }
                        is FileOperationResult.Failure -> {
                            val errorText = if (result.errorRes != null) {
                                appCtx.getString(result.errorRes, *result.formatArgs.toTypedArray())
                            } else if (result.error.contains("already exists", ignoreCase = true)) {
                                appCtx.getString(com.sza.fastmediasorter.R.string.error_file_exists_copy, currentFile.name, destination.name)
                            } else {
                                result.error
                            }

                            val message = if (result.errorRes != null) errorText
                                         else appCtx.getString(com.sza.fastmediasorter.R.string.error_copy_failed, errorText)

                            callback.onOperationError(message, null)
                        }
                        is FileOperationResult.AuthenticationRequired -> {
                            callback.onAuthenticationRequired(result.provider, result.message)
                        }
                        is FileOperationResult.PermissionRequired -> {
                            callback.onOperationError(appCtx.getString(com.sza.fastmediasorter.R.string.error_delete_unexpected))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: Copy operation failed")
                if (!isActivityGone()) {
                    withContext(Dispatchers.Main) {
                        callback.onOperationError(appCtx.getString(com.sza.fastmediasorter.R.string.error_copy_failed, e.message), e)
                    }
                }
            }
        }
    }

    /**
     * Perform move operation to destination resource.
     */
    fun performMove(destination: MediaResource) {
        val currentFile = callback.getCurrentFile() ?: return
        if (moveInProgress) return
        moveInProgress = true
        callback.onBeforeMove(currentFile.path)

        // Same scope reasoning as performCopy: outlive Activity destruction.
        appScope.launch {
            val destinationReachabilityError = checkSmbDestinationReachability(destination)
            if (destinationReachabilityError != null) {
                if (!isActivityGone()) {
                    withContext(Dispatchers.Main) {
                        callback.onOperationError(destinationReachabilityError, null)
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (!isActivityGone()) {
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(com.sza.fastmediasorter.R.string.msg_move_started, destination.name),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

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

                withContext(Dispatchers.Main) {
                    if (isActivityGone()) {
                        Timber.i("FileOperationsHandler: move completed after Activity destroyed — skipping UI callbacks (result=${result::class.simpleName})")
                        return@withContext
                    }
                    when (result) {
                        is FileOperationResult.Success -> {
                            Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_move_success, destination.name), Toast.LENGTH_SHORT).show()
                            callback.onMoveSuccess(destination, currentFile.path, true)
                        }
                        is FileOperationResult.PartialSuccess -> {
                            val successCount = result.processedCount
                            Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_move_success_count, successCount, destination.name), Toast.LENGTH_SHORT).show()
                            callback.onMoveSuccess(destination, currentFile.path, true)
                        }
                        is FileOperationResult.Failure -> {
                            val errorText = if (result.errorRes != null) {
                                appCtx.getString(result.errorRes, *result.formatArgs.toTypedArray())
                            } else if (result.error.contains("already exists", ignoreCase = true)) {
                                appCtx.getString(com.sza.fastmediasorter.R.string.error_file_exists_move, currentFile.name, destination.name)
                            } else {
                                result.error
                            }

                            val message = if (result.errorRes != null) errorText
                                         else appCtx.getString(com.sza.fastmediasorter.R.string.error_move_failed, errorText)

                            callback.onOperationError(message, null)
                        }
                        is FileOperationResult.AuthenticationRequired -> {
                            callback.onAuthenticationRequired(result.provider, result.message)
                        }
                        is FileOperationResult.PermissionRequired -> {
                            callback.onBatchDeletePermissionRequired(result.pendingIntent)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: Move operation failed")
                moveInProgress = false
                if (!isActivityGone()) {
                    withContext(Dispatchers.Main) {
                        callback.onOperationError(
                            appCtx.getString(com.sza.fastmediasorter.R.string.error_move_failed, e.message ?: e.javaClass.simpleName),
                            e
                        )
                    }
                }
            }
        }
    }

    fun performCopyToPath(destinationPath: String) {
        val currentFile = callback.getCurrentFile() ?: return
        appScope.launch {
            val settings = settingsRepository.getSettings().first()
            withContext(Dispatchers.Main) {
                if (!isActivityGone()) {
                    val folderName = java.io.File(destinationPath).name
                    Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_copy_started, folderName), Toast.LENGTH_LONG).show()
                }
            }
            try {
                val sourceFile = createNetworkAwareFile(currentFile.path, currentFile.name)
                val operation = FileOperation.Copy(
                    sources = listOf(sourceFile),
                    destination = java.io.File(destinationPath),
                    overwrite = settings.overwriteOnCopy,
                    sourceCredentialsId = callback.getCurrentResource()?.credentialsId
                )
                val result = fileOperationUseCase.execute(operation)
                withContext(Dispatchers.Main) {
                    if (isActivityGone()) return@withContext
                    val folderName = java.io.File(destinationPath).name
                    when (result) {
                        is FileOperationResult.Success -> {
                            Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_copy_success, folderName), Toast.LENGTH_SHORT).show()
                            callback.onCopyToPathSuccess(destinationPath, settings.goToNextAfterCopy)
                        }
                        is FileOperationResult.PartialSuccess -> {
                            Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_copy_success_count, result.processedCount, folderName), Toast.LENGTH_SHORT).show()
                            if (settings.goToNextAfterCopy) callback.onCopyToPathSuccess(destinationPath, true)
                        }
                        is FileOperationResult.Failure -> {
                            val msg = if (result.errorRes != null) appCtx.getString(result.errorRes, *result.formatArgs.toTypedArray())
                                      else appCtx.getString(com.sza.fastmediasorter.R.string.error_copy_failed, result.error)
                            callback.onOperationError(msg, null)
                        }
                        is FileOperationResult.AuthenticationRequired -> callback.onAuthenticationRequired(result.provider, result.message)
                        is FileOperationResult.PermissionRequired -> callback.onOperationError(appCtx.getString(com.sza.fastmediasorter.R.string.error_delete_unexpected))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: performCopyToPath failed")
                if (!isActivityGone()) {
                    withContext(Dispatchers.Main) {
                        callback.onOperationError(appCtx.getString(com.sza.fastmediasorter.R.string.error_copy_failed, e.message), e)
                    }
                }
            }
        }
    }

    fun performMoveToPath(destinationPath: String) {
        val currentFile = callback.getCurrentFile() ?: return
        if (moveInProgress) return
        moveInProgress = true
        callback.onBeforeMove(currentFile.path)

        appScope.launch {
            val settings = settingsRepository.getSettings().first()
            withContext(Dispatchers.Main) {
                if (!isActivityGone()) {
                    val folderName = java.io.File(destinationPath).name
                    Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_move_started, folderName), Toast.LENGTH_LONG).show()
                }
            }
            try {
                val sourceFile = createNetworkAwareFile(currentFile.path, currentFile.name)
                val operation = FileOperation.Move(
                    sources = listOf(sourceFile),
                    destination = java.io.File(destinationPath),
                    overwrite = settings.overwriteOnMove,
                    sourceCredentialsId = callback.getCurrentResource()?.credentialsId
                )
                val result = fileOperationUseCase.execute(operation)
                withContext(Dispatchers.Main) {
                    if (isActivityGone()) return@withContext
                    val folderName = java.io.File(destinationPath).name
                    when (result) {
                        is FileOperationResult.Success -> {
                            Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_move_success, folderName), Toast.LENGTH_SHORT).show()
                            callback.onMoveToPathSuccess(destinationPath, currentFile.path, true)
                        }
                        is FileOperationResult.PartialSuccess -> {
                            Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_move_success_count, result.processedCount, folderName), Toast.LENGTH_SHORT).show()
                            callback.onMoveToPathSuccess(destinationPath, currentFile.path, true)
                        }
                        is FileOperationResult.Failure -> {
                            val msg = if (result.errorRes != null) appCtx.getString(result.errorRes, *result.formatArgs.toTypedArray())
                                      else appCtx.getString(com.sza.fastmediasorter.R.string.error_move_failed, result.error)
                            callback.onOperationError(msg, null)
                        }
                        is FileOperationResult.AuthenticationRequired -> callback.onAuthenticationRequired(result.provider, result.message)
                        is FileOperationResult.PermissionRequired -> callback.onBatchDeletePermissionRequired(result.pendingIntent)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: performMoveToPath failed")
                moveInProgress = false
                if (!isActivityGone()) {
                    withContext(Dispatchers.Main) {
                        callback.onOperationError(
                            appCtx.getString(com.sza.fastmediasorter.R.string.error_move_failed, e.message ?: e.javaClass.simpleName),
                            e
                        )
                    }
                }
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
        if (deleteInProgress) return
        deleteInProgress = true
        callback.onBeforeDelete(currentFile.path)

        Timber.d("FileOperationsHandler.performDelete: Starting delete for ${currentFile.path}")

        lifecycleScope.launch {
            try {
                val sourceFile = createNetworkAwareFile(currentFile.path, currentFile.name)
                
                Timber.d("FileOperationsHandler.performDelete: Source file created: ${sourceFile.path}")
                
                // Determine if soft-delete is possible (writable local paths only).
                // Android/media and non-local schemes must go through direct delete flow.
                val canUseSoftDelete = DeletePathPolicy.canUseSoftDelete(currentFile.path)
                
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
                        deleteInProgress = false
                        callback.onBatchDeletePermissionRequired(result.pendingIntent)
                    }
                    is FileOperationResult.Failure -> {
                        Timber.e("FileOperationsHandler.performDelete: Delete FAILED - ${result.error}")
                        deleteInProgress = false
                        val message = if (result.errorRes != null) {
                            context.getString(result.errorRes, *result.formatArgs.toTypedArray())
                        } else {
                            context.getString(com.sza.fastmediasorter.R.string.error_delete_failed, result.error)
                        }
                        callback.onOperationError(message, null)
                    }
                    else -> {
                        Timber.e("FileOperationsHandler.performDelete: Unexpected result type: ${result::class.simpleName}")
                        deleteInProgress = false
                        callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_delete_unexpected))
                    }
                }
            } catch (securityException: android.app.RecoverableSecurityException) {
                // Android 10+ requires user permission to delete from shared storage
                Timber.w("FileOperationsHandler: RecoverableSecurityException caught, requesting user permission")
                deleteInProgress = false
                callback.onOperationError(
                    context.getString(com.sza.fastmediasorter.R.string.error_delete_permission_required),
                    securityException
                )
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: Delete operation failed with exception")
                deleteInProgress = false
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
     * Fast fail for unavailable SMB destination to ensure user sees immediate feedback.
     * Returns localized error message if destination is unreachable, otherwise null.
     */
    private suspend fun checkSmbDestinationReachability(destination: MediaResource): String? {
        if (!destination.path.startsWith("smb://", ignoreCase = true)) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val endpoint = destination.path.removePrefix("smb://").substringBefore("/")
                if (endpoint.isBlank()) {
                    return@withContext context.getString(com.sza.fastmediasorter.R.string.error_connection_failed_generic, destination.name)
                }

                val host = endpoint.substringBefore(":")
                val port = endpoint.substringAfter(":", "445").toIntOrNull() ?: 445

                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 2000)
                }

                null
            } catch (e: Exception) {
                Timber.w(e, "FileOperationsHandler: SMB destination unreachable: ${destination.path}")
                context.getString(com.sza.fastmediasorter.R.string.error_connection_failed_generic, destination.name)
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
     * Delete current file with optional confirmation dialog based on Safe Mode settings.
     */
    fun deleteCurrentFile() {
        val resource = callback.getCurrentResource()
        if (resource?.isReadOnly == true) {
            Toast.makeText(context, context.getString(com.sza.fastmediasorter.R.string.error_read_only), Toast.LENGTH_SHORT).show()
            return
        }
        val currentFile = callback.getCurrentFile()
        if (currentFile == null) {
            Toast.makeText(context, context.getString(com.sza.fastmediasorter.R.string.msg_no_file_to_delete), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            val shouldConfirm = settings.enableSafeMode || settings.confirmDelete
            Timber.d("FileOperationsHandler.deleteCurrentFile: shouldConfirm=$shouldConfirm")
            if (shouldConfirm) {
                val activity = context as? android.app.Activity
                if (activity?.isFinishing == true || activity?.isDestroyed == true) {
                    Timber.w("FileOperationsHandler.deleteCurrentFile: Activity is finishing/destroyed, skipping confirm dialog")
                    return@launch
                }
                AlertDialog.Builder(context)
                    .setTitle(com.sza.fastmediasorter.R.string.confirm_delete_title)
                    .setMessage(context.getString(com.sza.fastmediasorter.R.string.confirm_delete_message, 1))
                    .setPositiveButton(com.sza.fastmediasorter.R.string.delete) { _, _ -> performDelete() }
                    .setNegativeButton(com.sza.fastmediasorter.R.string.cancel, null)
                    .show()
            } else {
                performDelete()
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
