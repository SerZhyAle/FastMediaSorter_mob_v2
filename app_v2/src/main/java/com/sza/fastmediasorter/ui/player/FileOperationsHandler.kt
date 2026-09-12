package com.sza.fastmediasorter.ui.player

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferRequest
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferSource
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperation
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperationQueue
import com.sza.fastmediasorter.util.showBoundToHost
import com.sza.fastmediasorter.utils.SafHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val playerFileOperationQueue: PlayerFileOperationQueue,
    private val browseTransferCoordinator: BrowseFileTransferCoordinator,
    private val callback: FileOperationCallback,
) {
    /**
     * App-context wrapper for Toasts that may fire after Activity is destroyed.
     * Activity context is invalid for view-system callbacks once the window is gone.
     */
    private val appCtx: Context get() = context.applicationContext

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

            val settings = settingsRepository.getSettings().first()
            val request = BrowseFileTransferRequest(
                operationType = FileOperationType.COPY,
                sourceResourceId = callback.getCurrentResource()?.id ?: -1L,
                sourceResourceName = callback.getCurrentResource()?.name.orEmpty(),
                sourceCredentialsId = callback.getCurrentResource()?.credentialsId,
                currentBrowsePath = null,
                destinationPath = destination.path,
                destinationName = destination.name,
                destinationResourceId = destination.id,
                overwriteFiles = settings.overwriteOnCopy,
                sources = listOf(
                    BrowseFileTransferSource(
                        path = currentFile.path,
                        displayName = currentFile.name,
                        size = currentFile.size,
                        isDirectory = currentFile.isDirectory,
                    ),
                ),
            )

            browseTransferCoordinator.enqueue(request)

            withContext(Dispatchers.Main) {
                if (!isActivityGone()) {
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(com.sza.fastmediasorter.R.string.msg_copy_started, destination.name),
                        Toast.LENGTH_LONG,
                    ).show()
                    if (settings.goToNextAfterCopy) {
                        callback.onCopySuccess(destination, true)
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
        callback.onBeforeMove(currentFile.path)

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

            val settings = settingsRepository.getSettings().first()
            val request = BrowseFileTransferRequest(
                operationType = FileOperationType.MOVE,
                sourceResourceId = callback.getCurrentResource()?.id ?: -1L,
                sourceResourceName = callback.getCurrentResource()?.name.orEmpty(),
                sourceCredentialsId = callback.getCurrentResource()?.credentialsId,
                currentBrowsePath = null,
                destinationPath = destination.path,
                destinationName = destination.name,
                destinationResourceId = destination.id,
                overwriteFiles = settings.overwriteOnMove,
                sources = listOf(
                    BrowseFileTransferSource(
                        path = currentFile.path,
                        displayName = currentFile.name,
                        size = currentFile.size,
                        isDirectory = currentFile.isDirectory,
                    ),
                ),
            )

            browseTransferCoordinator.enqueue(request)

            withContext(Dispatchers.Main) {
                if (!isActivityGone()) {
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(com.sza.fastmediasorter.R.string.msg_move_started, destination.name),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    fun performCopyToPath(destinationPath: String) {
        val currentFile = callback.getCurrentFile() ?: return
        appScope.launch {
            val settings = settingsRepository.getSettings().first()
            val folderName = destinationLabel(destinationPath)
            val request = BrowseFileTransferRequest(
                operationType = FileOperationType.COPY,
                sourceResourceId = callback.getCurrentResource()?.id ?: -1L,
                sourceResourceName = callback.getCurrentResource()?.name.orEmpty(),
                sourceCredentialsId = callback.getCurrentResource()?.credentialsId,
                currentBrowsePath = null,
                destinationPath = destinationPath,
                destinationName = folderName,
                destinationResourceId = null,
                overwriteFiles = settings.overwriteOnCopy,
                sources = listOf(
                    BrowseFileTransferSource(
                        path = currentFile.path,
                        displayName = currentFile.name,
                        size = currentFile.size,
                        isDirectory = currentFile.isDirectory,
                    ),
                ),
            )

            browseTransferCoordinator.enqueue(request)

            withContext(Dispatchers.Main) {
                if (!isActivityGone()) {
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(com.sza.fastmediasorter.R.string.msg_copy_started, folderName),
                        Toast.LENGTH_LONG,
                    ).show()
                    if (settings.goToNextAfterCopy) {
                        callback.onCopyToPathSuccess(destinationPath, true)
                    }
                }
            }
        }
    }

    fun performMoveToPath(destinationPath: String) {
        val currentFile = callback.getCurrentFile() ?: return
        callback.onBeforeMove(currentFile.path)

        appScope.launch {
            val settings = settingsRepository.getSettings().first()
            val folderName = destinationLabel(destinationPath)
            val request = BrowseFileTransferRequest(
                operationType = FileOperationType.MOVE,
                sourceResourceId = callback.getCurrentResource()?.id ?: -1L,
                sourceResourceName = callback.getCurrentResource()?.name.orEmpty(),
                sourceCredentialsId = callback.getCurrentResource()?.credentialsId,
                currentBrowsePath = null,
                destinationPath = destinationPath,
                destinationName = folderName,
                destinationResourceId = null,
                overwriteFiles = settings.overwriteOnMove,
                sources = listOf(
                    BrowseFileTransferSource(
                        path = currentFile.path,
                        displayName = currentFile.name,
                        size = currentFile.size,
                        isDirectory = currentFile.isDirectory,
                    ),
                ),
            )

            browseTransferCoordinator.enqueue(request)

            withContext(Dispatchers.Main) {
                if (!isActivityGone()) {
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(com.sza.fastmediasorter.R.string.msg_move_started, folderName),
                        Toast.LENGTH_LONG,
                    ).show()
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
