package com.sza.fastmediasorter.ui.browse.undo

import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.UndoOperation
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
    private val callbacks: UndoCallbacks
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
    }
    
    companion object {
        private const val UNDO_EXPIRY_MS = 10_000L // 10 seconds
    }
    
    /**
     * Save undo operation with current timestamp.
     * Shows toast notification to user.
     */
    fun saveOperation(operation: UndoOperation) {
        _undoState.value = UndoState(
            lastOperation = operation,
            undoOperationTimestamp = System.currentTimeMillis()
        )
        
        Timber.d("saveOperation: ${operation.type}, ${operation.sourceFiles.size} files")
        
        val operationType = when (operation.type) {
            FileOperationType.COPY -> "copied"
            FileOperationType.MOVE -> "moved"
            FileOperationType.DELETE -> "deleted"
            FileOperationType.RENAME -> "renamed"
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
            callbacks.showMessage("No operation to undo")
            return false
        }
        
        try {
            when (operation.type) {
                FileOperationType.COPY -> undoCopyOperation(operation)
                FileOperationType.MOVE -> undoMoveOperation(operation)
                FileOperationType.DELETE -> undoDeleteOperation(operation)
                FileOperationType.RENAME -> undoRenameOperation(operation)
            }
            
            // Clear undo operation after successful execution
            _undoState.value = UndoState()
            return true
            
        } catch (e: Exception) {
            Timber.e(e, "Undo operation failed")
            callbacks.showError(
                message = "Undo failed: ${e.message}",
                details = e.stackTraceToString(),
                exception = e
            )
            return false
        }
    }
    
    /**
     * Undo COPY: Delete copied files from destination.
     */
    private suspend fun undoCopyOperation(operation: UndoOperation) {
        operation.copiedFiles?.forEach { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
                Timber.d("undoCopy: deleted $path")
            }
        }
        callbacks.showMessage("Undo: copy operation cancelled")
        // No reload needed - files were copied to destination, not this folder
    }
    
    /**
     * Undo MOVE: Move files back to original location.
     */
    private suspend fun undoMoveOperation(operation: UndoOperation) {
        val restoredFiles = mutableListOf<MediaFile>()
        
        operation.copiedFiles?.forEachIndexed { index, destPath ->
            val sourcePath = operation.sourceFiles.getOrNull(index)
            if (sourcePath != null) {
                val destFile = File(destPath)
                val sourceFile = File(sourcePath)
                if (destFile.exists() && destFile.renameTo(sourceFile)) {
                    Timber.d("undoMove: $destPath -> $sourcePath")
                    restoredFiles.add(callbacks.createMediaFileFromFile(sourceFile))
                }
            }
        }
        
        callbacks.showMessage("Undo: restored ${restoredFiles.size} file(s)")
        
        if (restoredFiles.isNotEmpty()) {
            callbacks.addFilesToList(restoredFiles)
        }
    }
    
    /**
     * Undo DELETE: Restore files from trash folder.
     * Trash folder pattern: .trash_{timestamp}/
     */
    private suspend fun undoDeleteOperation(operation: UndoOperation) {
        val paths = operation.copiedFiles
        if (paths.isNullOrEmpty()) {
            callbacks.showMessage("Undo: no files to restore")
            return
        }
        
        // Extract trash directories from beginning of path list
        val trashDirs = mutableListOf<File>()
        var idx = 0
        while (idx < paths.size) {
            val candidate = File(paths[idx])
            if (candidate.exists() && candidate.isDirectory && candidate.name.startsWith(".trash_")) {
                trashDirs.add(candidate)
                idx++
            } else {
                break
            }
        }
        
        val originalPaths = if (idx < paths.size) paths.drop(idx) else emptyList()
        
        if (trashDirs.isEmpty()) {
            callbacks.showMessage("Undo: trash folder not found")
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
        
        callbacks.showMessage("Undo: restored ${restoredFiles.size} file(s)")
        
        if (restoredFiles.isNotEmpty()) {
            callbacks.addFilesToList(restoredFiles)
        }
    }
    
    /**
     * Undo RENAME: Rename files back to original names.
     * Requires full file list reload.
     */
    private suspend fun undoRenameOperation(operation: UndoOperation) {
        operation.oldNames?.forEach { (oldPath, newPath) ->
            val newFile = File(newPath)
            val oldFile = File(oldPath)
            if (newFile.exists()) {
                newFile.renameTo(oldFile)
                Timber.d("undoRename: $newPath -> $oldPath")
            }
        }
        
        callbacks.showMessage("Undo: rename operation cancelled")
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
