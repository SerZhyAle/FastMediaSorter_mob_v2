package com.sza.fastmediasorter.ui.player.standalone

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.ui.player.StandalonePlayerViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Custom-path («..») destination picker behind the standalone hosts' Copy/Move.
 *
 * The system tree picker runs in another task, so Android may kill this process while it is open. The
 * pending operation therefore lives in the host's saved state, not in a field, or the recreated host
 * would receive the picked tree with nothing to apply it to. The transfer also waits for the first
 * state that carries a file: after recreation the result is delivered on start, before the ViewModel
 * has reloaded the file from the intent, and the transfer handler silently drops a null current file.
 *
 * Construct it as a host field initializer - the launcher must be registered before the host is created.
 * [viewModelState] is read only when a tree arrives, so the Hilt ViewModel is not touched before onCreate.
 */
class StandaloneCustomPathPickManager(
    private val activity: ComponentActivity,
    private val viewModelState: () -> Flow<StandalonePlayerViewModel.StandalonePlayerState>,
    private val onTreePicked: (operation: FileOperationType, treeUri: String, label: String) -> Unit,
) {
    private var pendingOperation: FileOperationType? = null
    private var restoreConsumed = false

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val operation = takePendingOperation()
        if (uri != null && operation != null) deliver(operation, uri)
    }

    init {
        activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY) {
            bundleOf(KEY_OPERATION to pendingOperation?.name)
        }
    }

    fun launch(operation: FileOperationType) {
        // A launch after recreation owns the slot; a later lazy restore must not overwrite it.
        restoreConsumed = true
        pendingOperation = operation
        launcher.launch(null)
    }

    private fun takePendingOperation(): FileOperationType? {
        if (!restoreConsumed && activity.savedStateRegistry.isRestored) {
            restoreConsumed = true
            val saved = activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)
                ?.getString(KEY_OPERATION)
            if (saved != null) pendingOperation = FileOperationType.entries.firstOrNull { it.name == saved }
        }
        return pendingOperation.also { pendingOperation = null }
    }

    private fun deliver(operation: FileOperationType, uri: Uri) {
        activity.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val label = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() } ?: activity.getString(R.string.select_folder)
        activity.lifecycleScope.launch {
            viewModelState().first { it.mediaFile != null }
            onTreePicked(operation, uri.toString(), label)
        }
    }

    private companion object {
        const val STATE_KEY = "standalone_custom_path_pick"
        const val KEY_OPERATION = "pending_operation"
    }
}
