package com.sza.fastmediasorter.ui.player

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
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperation
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperationQueue
import com.sza.fastmediasorter.ui.player.fileops.createNetworkAwareFile
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
    private val playerFileOperationQueue: PlayerFileOperationQueue,
    private val callback: FileOperationCallback
) {
    /**
     * App-context wrapper for Toasts that may fire after Activity is destroyed.
     * Activity context is invalid for view-system callbacks once the window is gone.
     */
    private val appCtx: Context get() = context.applicationContext

    // Detailed player errors can show stack traces, so file-operation copy here must stay user-facing.
    private fun formatFailureMessage(
        result: FileOperationResult.Failure,
        fallbackRes: Int,
        alreadyExistsRes: Int? = null,
        vararg alreadyExistsArgs: Any
    ): String {
        return when {
            result.errorRes != null -> appCtx.getString(result.errorRes, *result.formatArgs.toTypedArray())
            alreadyExistsRes != null && result.error.contains("already exists", ignoreCase = true) -> {
                  appCtx.getString(alreadyExistsRes, *alreadyExistsArgs)
            }
            else -> appCtx.getString(fallbackRes)
        }
    }

    private fun reportOperationError(messageRes: Int) {
        callback.onOperationError(appCtx.getString(messageRes), null)
    }

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
        fun onCopyToPathSuccess(destinationPath: String, goToNext: Boolean)
        fun onOperationError(message: String, throwable: Throwable? = null)
        fun onAuthenticationRequired(provider: String, message: String)
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
                            // Surface SFTP partial failure (access-denied, copied-source-remains, etc.)
                            if (result.errors.isNotEmpty()) callback.onOperationError(result.errors.first(), null)
                        }
                        is FileOperationResult.Failure -> {
                            val message = formatFailureMessage(
                                result,
                                com.sza.fastmediasorter.R.string.error_copy_failed,
                                com.sza.fastmediasorter.R.string.error_file_exists_copy,
                                currentFile.name,
                                destination.name
                            )
                            callback.onOperationError(message, null)
                        }
                        is FileOperationResult.AuthenticationRequired -> {
                            callback.onAuthenticationRequired(result.provider, result.message)
                        }
                        is FileOperationResult.PermissionRequired -> {
                            callback.onOperationError(appCtx.getString(com.sza.fastmediasorter.R.string.error_operation_failed))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: Copy operation failed")
                if (!isActivityGone()) {
                    withContext(Dispatchers.Main) {
                        reportOperationError(com.sza.fastmediasorter.R.string.error_copy_failed)
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
        val operation = PlayerFileOperation.moveToResource(
            currentFile = currentFile,
            currentResource = callback.getCurrentResource(),
            destination = destination,
        )
        callback.onBeforeMove(currentFile.path)
        playerFileOperationQueue.enqueue(operation)
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
                               // Surface SFTP partial failure (access-denied, copied-source-remains, etc.)
                               if (result.errors.isNotEmpty()) callback.onOperationError(result.errors.first(), null)
                        }
                        is FileOperationResult.Failure -> {
                            val msg = formatFailureMessage(result, com.sza.fastmediasorter.R.string.error_copy_failed)
                            callback.onOperationError(msg, null)
                        }
                        is FileOperationResult.AuthenticationRequired -> callback.onAuthenticationRequired(result.provider, result.message)
                        is FileOperationResult.PermissionRequired -> callback.onOperationError(appCtx.getString(com.sza.fastmediasorter.R.string.error_operation_failed))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "FileOperationsHandler: performCopyToPath failed")
                if (!isActivityGone()) {
                    withContext(Dispatchers.Main) {
                        reportOperationError(com.sza.fastmediasorter.R.string.error_copy_failed)
                    }
                }
            }
        }
    }

    fun performMoveToPath(destinationPath: String) {
        val currentFile = callback.getCurrentFile() ?: return
        val operation = PlayerFileOperation.moveToPath(
            currentFile = currentFile,
            currentResource = callback.getCurrentResource(),
            destinationPath = destinationPath,
        )
        callback.onBeforeMove(currentFile.path)
        playerFileOperationQueue.enqueue(operation)
    }

    /**
     * Delete current file with confirmation.
     */
    fun performDelete() {
        val currentFile = callback.getCurrentFile()
        if (currentFile == null) {
            Timber.e("FileOperationsHandler.performDelete: Current file is null!")
            callback.onOperationError(context.getString(com.sza.fastmediasorter.R.string.error_delete_unexpected))
            return
        }
        val operation = PlayerFileOperation.delete(
            currentFile = currentFile,
            currentResource = callback.getCurrentResource(),
        )
        callback.onBeforeDelete(currentFile.path)
        Timber.d("FileOperationsHandler.performDelete: Enqueuing delete for ${currentFile.path}")
        playerFileOperationQueue.enqueue(operation)
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
                reportOperationError(com.sza.fastmediasorter.R.string.error_share_failed)
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
                    callback.onOperationError(
                        formatFailureMessage(result, com.sza.fastmediasorter.R.string.error_share_download_failed)
                    )
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
                reportOperationError(com.sza.fastmediasorter.R.string.error_share_failed)
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

}
