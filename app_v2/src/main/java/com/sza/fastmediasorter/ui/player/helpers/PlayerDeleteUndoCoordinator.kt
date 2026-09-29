package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
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

/**
 * Owns PlayerViewModel's destructive-operation flow:
 *   - [deleteCurrentFile] - soft-delete to `.trash` for local files, hard-delete for network,
 *     saving an [UndoOperation] when the setting is enabled and the delete was soft.
 *   - [saveUndoOperation] / [clearExpiredUndoOperation] - undo lifecycle timestamp book-keeping
 *     (5-minute TTL).
 *   - [undoLastOperation] - renames the trashed copy back to its original local path.
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
                            val undoOp = UndoOperation(
                                type = FileOperationType.DELETE,
                                sourceFiles = listOf(currentFile.path),
                                destinationFolder = null,
                                copiedFiles = null,
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
                        val message = context.getString(R.string.error_delete_failed)
                        sendEvent(PlayerViewModel.PlayerEvent.ShowError(message))
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
                e.errorUnlessCancellation("Error deleting file: ${currentFile.path}")
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
                e.errorUnlessCancellation("Failed to reload files after rename")
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

    /**
     * Only a local soft delete records an undo, so the trashed copy is always on the local disk. It is
     * located by [FileOperationUseCase.findTrashedCopy] from the original path: the delete handler reports
     * originals, never trash paths.
     */
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
        val originalPath = operation.sourceFiles.firstOrNull()
        if (originalPath == null) {
            sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.no_files_to_restore)))
        } else {
            restoreInBackground(originalPath, operation.timestamp)
        }
    }

    private fun restoreInBackground(originalPath: String, operationTimestampMs: Long) {
        scope.launch {
            try {
                val outcome = restoreLocalFile(File(originalPath), operationTimestampMs)
                Timber.d("S3812: player undoDelete outcome=$outcome path=$originalPath")
                when (outcome) {
                    RestoreOutcome.RESTORED -> {
                        updateState { it.copy(lastOperation = null, undoOperationTimestamp = null) }
                        val message = context.getString(R.string.file_restored, File(originalPath).name)
                        sendEvent(PlayerViewModel.PlayerEvent.ShowMessage(message))
                        parentCallbacks.reloadFiles()
                    }
                    RestoreOutcome.NOT_FOUND -> sendEvent(
                        PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.invalid_undo_operation_data))
                    )
                    RestoreOutcome.FAILED -> sendEvent(
                        PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.failed_to_restore_files))
                    )
                }
            } catch (e: Exception) {
                e.errorUnlessCancellation("Undo operation failed")
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

    private enum class RestoreOutcome { RESTORED, NOT_FOUND, FAILED }

    private suspend fun restoreLocalFile(originalFile: File, operationTimestampMs: Long): RestoreOutcome =
        withContext(Dispatchers.IO) {
            val trashedFile = fileOperationUseCase.findTrashedCopy(originalFile, operationTimestampMs)
            if (trashedFile == null) {
                Timber.e("Undo: trashed copy not found for ${originalFile.absolutePath}")
                return@withContext RestoreOutcome.NOT_FOUND
            }
            // renameTo would replace a file that took the name after the delete.
            if (originalFile.exists()) {
                Timber.w("Undo: target already exists, left in trash: ${originalFile.absolutePath}")
                return@withContext RestoreOutcome.FAILED
            }
            if (!trashedFile.renameTo(originalFile)) {
                Timber.e("Undo: rename failed ${trashedFile.absolutePath} -> ${originalFile.absolutePath}")
                return@withContext RestoreOutcome.FAILED
            }
            val snapshotDir = trashedFile.parentFile
            if (snapshotDir?.listFiles()?.isEmpty() == true) {
                snapshotDir.delete()
            }
            Timber.i("Undo: restored local file ${originalFile.absolutePath}")
            RestoreOutcome.RESTORED
        }
    companion object {
        private const val UNDO_EXPIRY_MS = 5 * 60 * 1000L
    }
}
