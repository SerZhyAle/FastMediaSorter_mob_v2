package com.sza.fastmediasorter.ui.browse.undo

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File

/**
 * Manages undo operations for file operations in BrowseViewModel.
 * Handles 10-second expiry window and undo execution logic.
 *
 * Supported operations: Copy, Move, Delete (with trash), Rename
 */
class BrowseUndoManager(
    private val context: Context,
    private val callbacks: UndoCallbacks,
    private val statsSink: StatsSink,
) {

    private val _undoState = MutableStateFlow(UndoState())
    val undoState: StateFlow<UndoState> = _undoState.asStateFlow()

    data class UndoState(
        val lastOperation: UndoOperation? = null,
        val undoOperationTimestamp: Long? = null
    )

    interface UndoCallbacks {
        suspend fun addFilesToList(files: List<MediaFile>)
        suspend fun reloadFileList()
        fun createMediaFileFromFile(file: File): MediaFile
        fun showMessage(message: String)
        fun showUndoToast(operationType: String)
        fun showError(message: String, details: String?, exception: Throwable?)

        /** Reverse-rename [currentPath] back to [originalName] through the shared file-operations layer
         *  so undo works for local, network, and cloud schemes. Returns true on success. */
        suspend fun renameViaFileOperation(currentPath: String, originalName: String): Boolean

        /** Enqueue a reverse whole-tree transfer of [treePaths] into [destinationParent].
         *  Returns false when a transfer is already running. */
        suspend fun enqueueDirectoryUndoTransfer(treePaths: List<String>, destinationParent: String): Boolean

        /** Recursively delete the copied trees at [treePaths] through the operations layer.
         *  Returns how many entries were removed. */
        suspend fun deleteDirectoryTrees(treePaths: List<String>): Int

        /** Ask the user to authorise deleting [treeCount] copied trees. Returns false when declined. */
        suspend fun confirmDestructiveUndo(treeCount: Int): Boolean
    }

    companion object {
        private const val UNDO_EXPIRY_MS = 10_000L // 10 seconds
    }

    /**
     * Save undo operation and start its expiry window at the operation's own completion time.
     * Shows toast notification to user.
     *
     * S1326: the window must not restart here. A record replayed from disk after process death carries
     * the moment the transfer actually finished, and re-stamping it presented a folder move completed
     * an hour ago as freshly undoable - one press then rode whole trees back. A live operation is
     * unaffected: UndoOperation.timestamp defaults to now.
     */
    fun saveOperation(operation: UndoOperation) {
        _undoState.value = UndoState(
            lastOperation = operation,
            undoOperationTimestamp = operation.timestamp
        )

        Timber.d("saveOperation: ${operation.type}, ${operation.sourceFiles.size} files")

        val operationType = when (operation.type) {
            FileOperationType.COPY -> "copied"
            FileOperationType.MOVE -> "moved"
            FileOperationType.DELETE -> "deleted"
            FileOperationType.RENAME -> "renamed"
            // ARCHIVE is a destination-picker-only type - never persisted as an undo op.
            FileOperationType.ARCHIVE -> return
        }
        callbacks.showUndoToast(operationType)
    }

    /**
     * Execute undo for last operation if not expired.
     * Returns true if undo was attempted, false if no operation available.
     */
    suspend fun undoLastOperation(): Boolean {
        val operation = _undoState.value.lastOperation
        if (operation == null) {
            callbacks.showMessage(context.getString(R.string.no_operation_to_undo))
            return false
        }

        try {
            val performed = when (operation.type) {
                FileOperationType.COPY -> undoCopyOperation(operation)
                FileOperationType.MOVE -> undoMoveOperation(operation)
                FileOperationType.DELETE -> {
                    undoDeleteOperation(operation)
                    true
                }
                FileOperationType.RENAME -> {
                    undoRenameOperation(operation)
                    true
                }
                // ARCHIVE is a destination-picker-only type - never reaches the undo path.
                FileOperationType.ARCHIVE -> return false
            }

            // S1326: a refusal because another transfer is running is not a performed undo - the record
            // stays so the user can press undo again once that transfer finishes.
            if (performed) {
                _undoState.value = UndoState()
                statsSink.record(StatsEvent.UndoPerformed)
            }
            return performed
        } catch (e: Exception) {
            Timber.e(e, "Undo operation failed")
            callbacks.showError(
                message = context.getString(R.string.undo_failed),
                details = null,
                exception = null
            )
            return false
        }
    }

    /**
     * Undo COPY: delete the copies the operation created at the destination - loose files, whole trees,
     * or both when one record carries both halves.
     *
     * S1326: the confirmation gates the WHOLE record, not just its folder half. A decline must leave the
     * files alone too, because a half-applied undo clears the record and leaves the user nothing to press
     * a second time.
     */
    private suspend fun undoCopyOperation(operation: UndoOperation): Boolean {
        val directories = operation.copiedDirectories
        if (directories.isNotEmpty() && !callbacks.confirmDestructiveUndo(directories.size)) {
            return false
        }
        val removedFiles = deleteCopiedFiles(operation)
        if (directories.isNotEmpty()) {
            // Through the operations layer, never java.io.File.deleteRecursively: the file half below
            // still uses raw java.io.File and so no-ops on smb://, sftp://, ftp:// and cloud:// paths.
            callbacks.deleteDirectoryTrees(directories)
        }
        callbacks.showMessage(copyUndoMessage(directories.size, removedFiles))
        // No reload needed - the copies lived at the destination, not in this folder.
        return true
    }

    /**
     * Undo MOVE: send both halves of the record back to where they came from.
     *
     * S1326: the folder half is attempted first because only it can be refused. A refused enqueue means
     * nothing was reversed, so returning before the file half keeps the whole record retryable instead of
     * moving the files back under a record that then reports failure.
     */
    private suspend fun undoMoveOperation(operation: UndoOperation): Boolean {
        val folderOutcome = startDirectoryMoveUndo(operation)
        if (folderOutcome == DirectoryUndoOutcome.REFUSED) {
            callbacks.showMessage(context.getString(R.string.browse_transfer_already_running))
            return false
        }
        val restoredFiles = restoreMovedFiles(operation)
        callbacks.showMessage(moveUndoMessage(folderOutcome, restoredFiles.size))
        if (restoredFiles.isNotEmpty()) {
            callbacks.addFilesToList(restoredFiles)
        }
        return true
    }

    /** What the folder half of a move record did, so the caller can word one message for both halves. */
    private enum class DirectoryUndoOutcome { ABSENT, STARTED, INVALID, REFUSED }

    /**
     * Every folder in one record was selected from a single browse listing, so they share a parent and
     * one destination is correct. A record whose sources are not siblings is a defect where it was
     * written, not a case to reconcile at replay.
     */
    private suspend fun startDirectoryMoveUndo(operation: UndoOperation): DirectoryUndoOutcome {
        if (operation.copiedDirectories.isEmpty()) return DirectoryUndoOutcome.ABSENT
        val originalParent = operation.sourceDirectories.firstOrNull()
            ?.trimEnd('/')
            ?.substringBeforeLast('/')
            .orEmpty()
        return when {
            originalParent.isEmpty() -> DirectoryUndoOutcome.INVALID
            callbacks.enqueueDirectoryUndoTransfer(operation.copiedDirectories, originalParent) ->
                DirectoryUndoOutcome.STARTED
            else -> DirectoryUndoOutcome.REFUSED
        }
    }

    private fun deleteCopiedFiles(operation: UndoOperation): Int {
        var removed = 0
        operation.copiedFiles?.forEach { path ->
            val file = File(path)
            if (file.exists() && file.delete()) {
                removed++
                Timber.d("undoCopy: deleted $path")
            }
        }
        return removed
    }

    private fun restoreMovedFiles(operation: UndoOperation): List<MediaFile> {
        val restored = mutableListOf<MediaFile>()
        operation.copiedFiles?.forEachIndexed { index, destPath ->
            val sourcePath = operation.sourceFiles.getOrNull(index) ?: return@forEachIndexed
            val destFile = File(destPath)
            val sourceFile = File(sourcePath)
            if (destFile.exists() && destFile.renameTo(sourceFile)) {
                Timber.d("undoMove: $destPath -> $sourcePath")
                restored.add(callbacks.createMediaFileFromFile(sourceFile))
            }
        }
        return restored
    }

    private fun copyUndoMessage(directoryCount: Int, removedFiles: Int): String = when {
        directoryCount > 0 && removedFiles > 0 ->
            context.getString(R.string.undo_mixed_copy_done, removedFiles, directoryCount)
        directoryCount > 0 -> context.getString(R.string.undo_folder_copy_done)
        else -> context.getString(R.string.toast_copy_cancelled)
    }

    private fun moveUndoMessage(folderOutcome: DirectoryUndoOutcome, restoredCount: Int): String = when {
        folderOutcome == DirectoryUndoOutcome.INVALID ->
            context.getString(R.string.invalid_undo_operation_data)
        folderOutcome == DirectoryUndoOutcome.STARTED && restoredCount > 0 ->
            context.getString(R.string.undo_mixed_move_started, restoredCount)
        folderOutcome == DirectoryUndoOutcome.STARTED ->
            context.getString(R.string.undo_folder_move_started)
        else -> context.getString(R.string.files_restored, restoredCount)
    }

    /**
     * Undo DELETE: Restore files from trash folder.
     * Trash container name: .trash (canonical) or .trash_{timestamp} (legacy).
     */
    private suspend fun undoDeleteOperation(operation: UndoOperation) {
        val paths = operation.copiedFiles
        if (paths.isNullOrEmpty()) {
            callbacks.showMessage(context.getString(R.string.no_files_to_restore))
            return
        }

        // Extract trash directories from beginning of path list
        val trashDirs = mutableListOf<File>()
        var idx = 0
        while (idx < paths.size) {
            val candidate = File(paths[idx])
            if (candidate.exists() && candidate.isDirectory && TrashFolderContract.matchesTrashSegment(candidate.name)) {
                trashDirs.add(candidate)
                idx++
            } else {
                break
            }
        }

        val originalPaths = if (idx < paths.size) paths.drop(idx) else emptyList()

        if (trashDirs.isEmpty()) {
            callbacks.showMessage(context.getString(R.string.invalid_undo_operation_data))
            return
        }

        val restoredFiles = mutableListOf<MediaFile>()

        // Restore files from each trash directory
        trashDirs.forEach { trashDir ->
            if (!trashDir.exists() || !trashDir.isDirectory) return@forEach

            trashDir.listFiles()?.forEach { trashedFile ->
                val originalPath = originalPaths.find { it.endsWith(trashedFile.name) }
                if (originalPath != null) {
                    val originalFile = File(originalPath)
                    if (trashedFile.renameTo(originalFile)) {
                        Timber.d("undoDelete: restored ${trashedFile.name} from ${trashDir.absolutePath}")
                        restoredFiles.add(callbacks.createMediaFileFromFile(originalFile))
                    }
                }
            }

            // Remove trash directory if empty
            if (trashDir.listFiles()?.isEmpty() == true) {
                trashDir.delete()
            }
        }

        callbacks.showMessage(context.getString(R.string.files_restored, restoredFiles.size))

        if (restoredFiles.isNotEmpty()) {
            callbacks.addFilesToList(restoredFiles)
        }
    }

    /**
     * Undo RENAME: Rename files back to original names.
     * Requires full file list reload.
     */
    private suspend fun undoRenameOperation(operation: UndoOperation) {
        operation.oldNames?.forEach { (currentPath, originalName) ->
            val restored = callbacks.renameViaFileOperation(currentPath, originalName)
            Timber.d("undoRename: $currentPath -> $originalName (restored=$restored)")
        }

        callbacks.showMessage(context.getString(R.string.undo_rename_cancelled))
        callbacks.reloadFileList()
    }

    /**
     * Clear undo operation if expired (older than 10 seconds).
     * Call when activity resumes or before showing undo button.
     */
    fun clearIfExpired() {
        val current = _undoState.value
        val timestamp = current.undoOperationTimestamp

        if (timestamp != null && current.lastOperation != null) {
            val age = System.currentTimeMillis() - timestamp
            if (age > UNDO_EXPIRY_MS) {
                Timber.d("clearIfExpired: Undo expired (${age}ms > ${UNDO_EXPIRY_MS}ms)")
                _undoState.value = UndoState()
            }
        }
    }

    /**
     * Check if undo operation is available and not expired.
     */
    fun isUndoAvailable(): Boolean {
        clearIfExpired()
        return _undoState.value.lastOperation != null
    }
}
