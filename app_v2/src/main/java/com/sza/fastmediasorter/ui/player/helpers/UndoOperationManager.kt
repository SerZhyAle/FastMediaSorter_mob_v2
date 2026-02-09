package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.UndoOperation

class UndoOperationManager(
    private val rootView: View,
    private val callback: Callback
) {
    interface Callback {
        fun isActivityAlive(): Boolean
        fun getUndoActionText(): String
        fun onUndoRequested()
    }

    fun showUndoSnackbar(operation: UndoOperation) {
        if (!callback.isActivityAlive()) {
            return
        }

        val description = getOperationDescription(rootView.context, operation)
        Snackbar.make(
            rootView,
            description,
            Snackbar.LENGTH_LONG
        )
            .setAction(callback.getUndoActionText()) {
                callback.onUndoRequested()
            }
            .show()
    }

    private fun getOperationDescription(context: android.content.Context, operation: UndoOperation): String {
        val count = operation.sourceFiles.size
        
        return when (operation.type) {
            com.sza.fastmediasorter.domain.model.FileOperationType.DELETE -> {
                context.getString(R.string.deleted_n_files, count)
            }
            com.sza.fastmediasorter.domain.model.FileOperationType.COPY -> {
                val destination = operation.destinationFolder?.substringAfterLast('/') ?: "destination"
                context.getString(R.string.msg_copy_success_count, count, destination)
            }
            com.sza.fastmediasorter.domain.model.FileOperationType.MOVE -> {
                val destination = operation.destinationFolder?.substringAfterLast('/') ?: "destination"
                context.getString(R.string.msg_move_success_count, count, destination)
            }
            com.sza.fastmediasorter.domain.model.FileOperationType.RENAME -> {
                // If single file rename and we have old/new names, we could show them
                // But current string resource is generic count-based
                // "Renamed %1$d files" -> renamed_n_files
                // Let's check if we have renamed_n_files
                context.getString(R.string.renamed_n_files, count)
            }
        }
    }

    companion object {
        fun defaultUndoActionText(rootView: View): String {
            return rootView.context.getString(R.string.undo).uppercase()
        }
    }
}
