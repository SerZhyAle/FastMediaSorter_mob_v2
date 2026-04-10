package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.usecase.CreateDirectoryUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages folder-level operations for the Browse screen.
 *
 * Responsibilities:
 * - Create a directory in the current browsing context.
 * - Rename an existing directory.
 * - Emit user-visible success/error events and trigger a full resource reload.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition — IV.1).
 */
class BrowseDirectoryOpsManager(
    private val context: Context,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val unifiedFileOperationHandler: UnifiedFileOperationHandler,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val reloadResource: () -> Unit
) {
    /**
     * Create a new directory named [name] inside the current path.
     */
    fun createFolder(name: String) {
        scope.launch(ioDispatcher) {
            val resource = stateFlow.value.resource ?: return@launch
            val currentPath = stateFlow.value.currentPath ?: resource.path

            Timber.d("BrowseDirectoryOpsManager.createFolder: name=$name, currentPath=$currentPath")

            val result = createDirectoryUseCase(resource, currentPath, name)

            withContext(Dispatchers.Main) {
                result.onSuccess { newFolderPath ->
                    Timber.i("BrowseDirectoryOpsManager.createFolder: SUCCESS, path=$newFolderPath")
                    reloadResource()
                    sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.msg_folder_created, name)))
                }.onFailure { error ->
                    Timber.e(error, "BrowseDirectoryOpsManager.createFolder: FAILED")
                    sendEvent(BrowseEvent.ShowError(error.message ?: "Failed to create folder"))
                }
            }
        }
    }

    /**
     * Rename a directory at [path] to [newName] and reload the resource on success.
     */
    fun renameDirectory(path: String, newName: String) {
        scope.launch(ioDispatcher) {
            Timber.d("BrowseDirectoryOpsManager.renameDirectory: path=$path, newName=$newName")
            val result = unifiedFileOperationHandler.executeRenameDirectory(path, newName)

            withContext(Dispatchers.Main) {
                result.onSuccess {
                    Timber.i("BrowseDirectoryOpsManager.renameDirectory: SUCCESS -> $it")
                    reloadResource()
                    sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.msg_folder_renamed)))
                }.onFailure { error ->
                    Timber.e(error, "BrowseDirectoryOpsManager.renameDirectory: FAILED")
                    sendEvent(BrowseEvent.ShowError(error.message ?: "Failed to rename folder"))
                }
            }
        }
    }
}