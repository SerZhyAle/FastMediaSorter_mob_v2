package com.sza.fastmediasorter.ui.browse.undo

import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.DeletePathPolicy

/**
 * Whether a finished operation leaves a record the undo can act on, which is also whether the Undo
 * snackbar appears. Callers that must decide before the snackbar exists, such as a done toast that would
 * cover it, read the same answer the undo manager acts on.
 */
internal object UndoOfferPolicy {

    /**
     * The file half of copy and move undo runs on java.io.File, and a delete is reversible only where it
     * went to the local trash, so a scheme path in either half cannot be undone. The folder half and
     * rename go through the operations layer and stay undoable on every scheme.
     */
    fun isOffered(operation: UndoOperation): Boolean {
        val copiedFiles = operation.copiedFiles.orEmpty()
        return when (operation.type) {
            FileOperationType.COPY -> copiedFiles.all(::isLocalFilePath)
            FileOperationType.MOVE ->
                copiedFiles.all(::isLocalFilePath) && operation.sourceFiles.all(::isLocalFilePath)
            FileOperationType.DELETE -> DeletePathPolicy.canUseSoftDelete(copiedFiles + operation.sourceFiles)
            FileOperationType.RENAME, FileOperationType.ARCHIVE -> true
        }
    }

    private fun isLocalFilePath(path: String): Boolean =
        !path.contains("://") && !path.startsWith("content:", ignoreCase = true)
}
