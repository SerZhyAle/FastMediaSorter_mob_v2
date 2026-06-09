package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Owns PlayerViewModel's destructive-operation flow:
 *   - [deleteCurrentFile] - soft-delete to `.trash` for local files, hard-delete for network,
 *     saving an [UndoOperation] when the setting is enabled and the delete was soft.
 *   - [saveUndoOperation] / [clearExpiredUndoOperation] - undo lifecycle timestamp book-keeping
 *     (5-minute TTL).
 *   - [undoLastOperation] - restores the file via rename (local) or Move use-case (network).
 *   - [reloadAfterRename] - re-queries the current resource's file list and repoints the index.
 *
 * The coordinator mutates [PlayerViewModel.PlayerState] via the supplied `updateState`, emits
 * user-visible events via `sendEvent`, and calls the parent VM back through the `parentCallbacks`
 * bundle for cross-concern actions (`saveResumeState` / `reloadFiles`) that still live on the VM.
 */
class PlayerDeleteUndoCoordinator(
    private val context: Context,
    private val fileOperationUseCase: FileOperationUseCase,
    private val settingsRepository: SettingsRepository,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val scope: CoroutineScope,
    private val stateFlow: StateFlow<PlayerViewModel.PlayerState>,
    private val updateState: ((PlayerViewModel.PlayerState) -> PlayerViewModel.PlayerState) -> Unit,
    private val sendEvent: (PlayerViewModel.PlayerEvent) -> Unit,
    private val parentCallbacks: ParentCallbacks
) {

    interface ParentCallbacks {
        fun saveResumeState()
        fun reloadFiles()
    }

    /** Kicks off an async delete of the current file. Returns null because the result is async. */
    fun deleteCurrentFile(finishOnSuccess: Boolean = false): Boolean? {
        val currentFile = stateFlow.value.currentFile
        val resource = stateFlow.value.resource

        if (currentFile == null) {
            sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.msg_no_file_to_delete)))
            return false
        }

        if (resource == null) {
            sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.toast_resource_not_loaded)))
            return false
        }

        scope.launch {
            try {
                val isNetwork = currentFile.path.let {
                    it.startsWith("smb://") ||
                        it.startsWith("sftp://") ||
                        it.startsWith("ftp://") ||
                        it.startsWith("cloud://")
                }

                // Network files must use hard delete (softDelete = false) as trash is not supported.
                val file = if (isNetwork) {
                    object : File(currentFile.path) {
                        override fun getAbsolutePath(): String = currentFile.path
                        override fun getPath(): String = currentFile.path
                    }
                } else {
                    File(currentFile.path)
                }

                val settings = settingsRepository.getSettings().first()
                val useTrash = settings.useTrash
                val effectiveSoftDelete = useTrash && !isNetwork
                val deleteOperation = FileOperation.Delete(files = listOf(file), softDelete = effectiveSoftDelete)

                when (val result = fileOperationUseCase.execute(deleteOperation)) {
                    is FileOperationResult.Success,
                    is FileOperationResult.PartialSuccess -> {
                        val softDeleteFallbackUsed = when (result) {
                            is FileOperationResult.Success -> result.softDeleteFallbackPaths.isNotEmpty()
                            is FileOperationResult.PartialSuccess -> result.softDeleteFallbackPaths.isNotEmpty()
                            is FileOperationResult.Failure -> false
                            is FileOperationResult.AuthenticationRequired -> false
                            is FileOperationResult.PermissionRequired -> false
                        }

                        sendEvent(PlayerViewModel.PlayerEvent.FileModified(currentFile.path))

                        // S0360: editor-initiated delete returns to browse instead of advancing.
                        if (finishOnSuccess) {
                            MediaFilesCacheManager.removeFile(resource.id, currentFile.path)
                            sendEvent(PlayerViewModel.PlayerEvent.FinishActivity)
                            return@launch
                        }

                        if (settings.enableUndo && effectiveSoftDelete && !softDeleteFallbackUsed) {
                            val trashPaths = when (result) {
                                is FileOperationResult.Success -> result.copiedFilePaths
                                is FileOperationResult.PartialSuccess -> emptyList()
                                is FileOperationResult.Failure -> emptyList()
                                is FileOperationResult.AuthenticationRequired -> emptyList()
                                is FileOperationResult.PermissionRequired -> emptyList()
                            }

                            val undoOp = UndoOperation(
                                type = FileOperationType.DELETE,
                                sourceFiles = listOf(currentFile.path),
                                destinationFolder = null,
                                copiedFiles = trashPaths.takeIf { it.isNotEmpty() },
                                oldNames = null
                            )
                            saveUndoOperation(undoOp)
                            sendEvent(PlayerViewModel.PlayerEvent.ShowUndoSnackbar(undoOp))
                        } else if (softDeleteFallbackUsed) {
                            sendEvent(
                                PlayerViewModel.PlayerEvent.ShowMessage(
                                    context.getString(R.string.delete_trash_unavailable_fallback_to_hard_delete)
                                )
                            )
                        }

                        val updatedFiles = stateFlow.value.files.toMutableList()
                        val deletedIndex = stateFlow.value.currentIndex
                        updatedFiles.removeAt(deletedIndex)

                        MediaFilesCacheManager.removeFile(resource.id, currentFile.path)

                        if (updatedFiles.isEmpty()) {
                            sendEvent(PlayerViewModel.PlayerEvent.FinishActivity)
                        } else {
                            // Circular navigation: if last file was deleted, loop back to index 0.
                            val newIndex = if (deletedIndex >= updatedFiles.size) 0 else deletedIndex
                            updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
                            parentCallbacks.saveResumeState()
                            Timber.d("File deleted, new list size: ${updatedFiles.size}, newIndex=$newIndex")
                        }
                    }
                    is FileOperationResult.Failure -> {
                        sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.error_delete_failed)))
                        Timber.e("Delete failed: ${result.error}")
                    }
                    is FileOperationResult.AuthenticationRequired -> {
                        sendEvent(PlayerViewModel.PlayerEvent.CloudAuthRequired(result.provider, result.message))
                        Timber.w("Delete requires cloud authentication: ${result.provider}")
                    }
                    is FileOperationResult.PermissionRequired -> {
                        // Handled by FileOperationsHandler/PlayerActivity.
                        Timber.d("Delete requires permission (handled by Activity)")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting file: ${currentFile.path}")
                sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.error_delete_failed)))
            }
        }

        return null
    }

    fun reloadAfterRename() {
        scope.launch {
            try {
                val resource = stateFlow.value.resource ?: return@launch

                val files = getMediaFilesUseCase(
                    resource = resource,
                    sortMode = resource.sortMode,
                    sizeFilter = null
                ).first()

                val currentIndex = stateFlow.value.currentIndex
                val newIndex = if (currentIndex < files.size) currentIndex else 0

                updateState { it.copy(files = files, currentIndex = newIndex) }
                Timber.d("Files reloaded after rename, total: ${files.size}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to reload files after rename")
                sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.reload_files_failed)))
            }
        }
    }

    fun saveUndoOperation(operation: UndoOperation) {
        updateState {
            it.copy(
                lastOperation = operation,
                undoOperationTimestamp = System.currentTimeMillis()
            )
        }
        Timber.d("Saved undo operation: ${operation.type}, file: ${operation.sourceFiles.firstOrNull()}")
    }

    fun undoLastOperation() {
        val operation = stateFlow.value.lastOperation
        if (operation == null) {
            sendEvent(PlayerViewModel.PlayerEvent.ShowMessage(context.getString(R.string.no_operation_to_undo)))
            return
        }
        if (operation.type != FileOperationType.DELETE) {
            sendEvent(PlayerViewModel.PlayerEvent.ShowMessage(context.getString(R.string.undo_delete_only)))
            return
        }

        scope.launch {
            try {
                // copiedFiles structure: [0] = trashDirPath, [1..n] = originalFilePaths
                val paths = operation.copiedFiles
                if (paths == null) {
                    sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.no_files_to_restore)))
                    return@launch
                }
                if (paths.size < 2) {
                    sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.invalid_undo_operation_data)))
                    return@launch
                }

                val trashDirPath = paths[0]
                val originalPath = paths[1]

                val isLocal = !originalPath.startsWith("smb://") &&
                    !originalPath.startsWith("sftp://") &&
                    !originalPath.startsWith("ftp://") &&
                    !originalPath.startsWith("cloud:/")

                val restoreSuccess = if (isLocal) {
                    restoreLocalFile(trashDirPath, originalPath)
                } else {
                    restoreNetworkFile(trashDirPath, originalPath)
                }

                if (restoreSuccess) {
                    updateState { it.copy(lastOperation = null, undoOperationTimestamp = null) }
                    sendEvent(PlayerViewModel.PlayerEvent.ShowMessage(context.getString(R.string.file_restored, File(originalPath).name)))
                    parentCallbacks.reloadFiles()
                } else {
                    sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.failed_to_restore_files)))
                }
            } catch (e: Exception) {
                Timber.e(e, "Undo operation failed")
                sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.undo_failed)))
            }
        }
    }

    /** Clear undo operation older than 5 minutes. */
    fun clearExpiredUndoOperation() {
        val currentState = stateFlow.value
        val timestamp = currentState.undoOperationTimestamp
        if (timestamp == null || currentState.lastOperation == null) return

        val elapsed = System.currentTimeMillis() - timestamp
        if (elapsed > UNDO_EXPIRY_MS) {
            updateState { it.copy(lastOperation = null, undoOperationTimestamp = null) }
            Timber.d("Expired undo operation cleared")
        }
    }

    private suspend fun restoreLocalFile(trashDirPath: String, originalPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val trashDir = File(trashDirPath)
                val originalFile = File(originalPath)

                if (!trashDir.exists() || !trashDir.isDirectory) {
                    Timber.e("Undo: Trash folder not found: $trashDirPath")
                    return@withContext false
                }

                val trashedFile = File(trashDir, originalFile.name)
                if (!trashedFile.exists()) {
                    Timber.e("Undo: Trashed file not found: ${trashedFile.absolutePath}")
                    return@withContext false
                }

                val restored = trashedFile.renameTo(originalFile)
                if (restored) {
                    if (trashDir.listFiles()?.isEmpty() == true) {
                        trashDir.delete()
                        Timber.d("Undo: Cleaned up empty trash directory")
                    }
                    Timber.i("Undo: Successfully restored local file: $originalPath")
                } else {
                    Timber.e("Undo: Failed to rename trashed file back to original location")
                }
                restored
            } catch (e: Exception) {
                Timber.e(e, "Undo: Exception restoring local file")
                false
            }
        }

    private suspend fun restoreNetworkFile(trashDirPath: String, originalPath: String): Boolean {
        return try {
            val originalFile = File(originalPath)
            val fileName = originalFile.name
            val trashFilePath = "$trashDirPath/$fileName"
            Timber.d("Undo: Restoring network file from $trashFilePath to $originalPath")

            val parentDir = originalFile.parentFile
                ?: throw IOException("Cannot restore to root directory")

            val moveOperation = FileOperation.Move(
                sources = listOf(File(trashFilePath)),
                destination = parentDir,
                overwrite = true
            )

            when (val result = fileOperationUseCase.execute(moveOperation)) {
                is FileOperationResult.Success -> {
                    Timber.i("Undo: Successfully restored network file: $originalPath")
                    true
                }
                is FileOperationResult.PartialSuccess -> {
                    Timber.w("Undo: Partial success restoring file (might be OK)")
                    true
                }
                is FileOperationResult.Failure -> {
                    Timber.e("Undo: Failed to restore network file: ${result.error}")
                    false
                }
                is FileOperationResult.AuthenticationRequired -> {
                    Timber.w("Undo: Cloud authentication required: ${result.provider}")
                    false
                }
                is FileOperationResult.PermissionRequired -> {
                    Timber.w("Undo: Permission required (unexpected)")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Undo: Exception restoring network file")
            false
        }
    }

    companion object {
        private const val UNDO_EXPIRY_MS = 5 * 60 * 1000L
    }
}
