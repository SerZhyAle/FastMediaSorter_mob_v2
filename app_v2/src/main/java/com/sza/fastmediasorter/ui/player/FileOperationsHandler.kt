package com.sza.fastmediasorter.ui.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperation
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperationQueue
import com.sza.fastmediasorter.ui.player.fileops.createNetworkAwareFile
import com.sza.fastmediasorter.ui.player.helpers.FileCopyProgressDialog
import com.sza.fastmediasorter.util.showBoundToHost
import com.sza.fastmediasorter.utils.SafHelper
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

    private fun destinationLabel(destinationPath: String): String {
        if (destinationPath.startsWith("content:/")) {
            val normalized = SafHelper.normalizeContentUri(destinationPath)
            val treeName = SafHelper.getTreeRoot(appCtx, normalized)?.name
            if (!treeName.isNullOrBlank()) {
                return treeName
            }
            val resolved = runCatching {
                com.sza.fastmediasorter.core.util.UriPathResolver.getPath(appCtx, Uri.parse(normalized))
            }.getOrNull()
            return resolved?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: normalized
        }
        return File(destinationPath).name.ifBlank { destinationPath }
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
        // UI callbacks (Toast, navigation) are gated by isActivityGone() - when
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
                        Timber.i("FileOperationsHandler: copy completed after Activity destroyed - skipping UI callbacks (result=${result::class.simpleName})")
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
                e.errorUnlessCancellation("FileOperationsHandler: Copy operation failed")
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
                    val folderName = destinationLabel(destinationPath)
                    Toast.makeText(appCtx, appCtx.getString(com.sza.fastmediasorter.R.string.msg_copy_started, folderName), Toast.LENGTH_LONG).show()
                }
            }
            try {
                val sourceFile = createNetworkAwareFile(currentFile.path, currentFile.name)
                val operation = FileOperation.Copy(
                    sources = listOf(sourceFile),
                    destination = createNetworkAwareFile(destinationPath, null),
                    overwrite = settings.overwriteOnCopy,
                    sourceCredentialsId = callback.getCurrentResource()?.credentialsId
                )
                val result = fileOperationUseCase.execute(operation)
                withContext(Dispatchers.Main) {
                    if (isActivityGone()) return@withContext
                    val folderName = destinationLabel(destinationPath)
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
                e.errorUnlessCancellation("FileOperationsHandler: performCopyToPath failed")
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
                e.warnUnlessCancellation("FileOperationsHandler: SMB destination unreachable: ${destination.path}")
                context.getString(com.sza.fastmediasorter.R.string.error_connection_failed_generic, destination.name)
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
                MaterialAlertDialogBuilder(context, com.sza.fastmediasorter.R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
                    .setTitle(com.sza.fastmediasorter.R.string.confirm_delete_title)
                    .setMessage(context.getString(com.sza.fastmediasorter.R.string.confirm_delete_message, 1))
                    .setPositiveButton(com.sza.fastmediasorter.R.string.delete) { _, _ -> performDelete() }
                    .setNegativeButton(com.sza.fastmediasorter.R.string.cancel, null)
                    .showBoundToHost(context)
            } else {
                performDelete()
            }
        }
    }

}
