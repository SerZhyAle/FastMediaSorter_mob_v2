package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.DeleteByFileSizeUseCase
import com.sza.fastmediasorter.domain.usecase.DeleteDirectoriesUseCase
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.DeletePathPolicy
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages file and directory deletion operations in the Browse screen.
 *
 * Responsibilities:
 * - Delete selected files (soft-delete when possible, hard-delete otherwise).
 * - Delete selected directories via [DeleteDirectoriesUseCase].
 * - Handle Android 11+ batch-delete permission grant callback.
 * - Scan/delete files by size ([scanBySize], [executeBySizeDeleteConfirmed]).
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition — IV.1).
 */
class BrowseDeleteManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val fileOperationUseCase: FileOperationUseCase,
    private val deleteDirectoriesUseCase: DeleteDirectoriesUseCase,
    private val deleteByFileSizeUseCase: DeleteByFileSizeUseCase,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val setIgnoringFileChanges: (Boolean) -> Unit,
    private val clearSelection: () -> Unit,
    private val removeFiles: (List<String>) -> Unit,
    private val saveUndoOperation: (UndoOperation) -> Unit,
    private val reloadFiles: () -> Unit,
    private val loadResource: () -> Unit
) {
    // Prefer structured delete failures so browse surfaces reuse localized copy when handlers provide it.
    private fun formatFailureDetails(result: FileOperationResult.Failure): String {
        return if (result.errorRes != null) {
            context.getString(result.errorRes, *result.formatArgs.toTypedArray())
        } else {
            result.error.ifBlank { context.getString(R.string.error_reason_unknown) }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Delete currently selected files.
     * Uses soft-delete (trash) when possible; hard-delete otherwise.
     * Emits [BrowseEvent.PermissionRequired] for Android 11+ scoped-storage batch ops.
     */
    fun deleteSelectedFiles() {
        Timber.d("BrowseDeleteManager.deleteSelectedFiles: ===== START =====")
        scope.launch(ioDispatcher) {
            val selectedPaths = stateFlow.value.selectedFiles.toList()
            if (selectedPaths.isEmpty()) {
                sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.no_files_selected)))
                return@launch
            }

            setLoading(true)

            val allFiles = stateFlow.value.mediaFiles
            val selectedSet = stateFlow.value.selectedFiles
            val (dirItems, fileItems) = allFiles
                .filter { it.path in selectedSet }
                .partition { it.isDirectory }

            val resolvedPaths = (dirItems + fileItems).map { it.path }.toSet()
            val unresolvedFilePaths = selectedPaths.filter { it !in resolvedPaths }

            val filesToDelete = (fileItems.map { it.path } + unresolvedFilePaths).map { path ->
                if (path.startsWith("smb://") || path.startsWith("sftp://") ||
                    path.startsWith("ftp://") || path.startsWith("cloud://")
                ) {
                    object : java.io.File(path) {
                        override fun getAbsolutePath(): String = path
                        override fun getPath(): String = path
                    }
                } else {
                    java.io.File(path)
                }
            }

            val canUseSoftDelete = DeletePathPolicy.canUseSoftDelete(selectedPaths)
            val useTrash = settingsRepository.getSettings().first().useTrash
            val effectiveSoftDelete = useTrash && canUseSoftDelete

            if (selectedPaths.size > 10) {
                sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.deleting_n_files, selectedPaths.size)))
            }

            setIgnoringFileChanges(true)

            var totalFileCount = 0
            var dirDeleteError: String? = null

            // --- Delete regular files ---
            if (filesToDelete.isNotEmpty()) {
                val deleteOperation = FileOperation.Delete(
                    files = filesToDelete,
                    softDelete = effectiveSoftDelete
                )
                Timber.d("BrowseDeleteManager: deleting ${filesToDelete.size} files")
                when (val result = fileOperationUseCase.execute(deleteOperation)) {
                    is FileOperationResult.Success -> {
                        totalFileCount += result.processedCount
                        Timber.i("BrowseDeleteManager: deleted ${result.processedCount} files")
                        if (effectiveSoftDelete) {
                            val undoOp = UndoOperation(
                                type = FileOperationType.DELETE,
                                sourceFiles = fileItems.map { it.path } + unresolvedFilePaths,
                                destinationFolder = null,
                                copiedFiles = result.copiedFilePaths,
                                oldNames = null
                            )
                            saveUndoOperation(undoOp)
                        }
                    }
                    is FileOperationResult.PartialSuccess -> {
                        totalFileCount += result.processedCount
                        Timber.w("BrowseDeleteManager: partial delete ${result.processedCount}/${filesToDelete.size}")
                    }
                    is FileOperationResult.Failure -> {
                        Timber.e("BrowseDeleteManager: failure — ${result.error}")
                        setIgnoringFileChanges(false)
                        setLoading(false)
                        // Keep the headline human and move filesystem detail to the secondary details surface.
                        sendEvent(
                            BrowseEvent.ShowError(
                                message = context.getString(R.string.all_delete_operations_failed),
                                details = formatFailureDetails(result)
                            )
                        )
                        return@launch
                    }
                    is FileOperationResult.AuthenticationRequired -> {
                        setIgnoringFileChanges(false)
                        setLoading(false)
                        sendEvent(BrowseEvent.CloudAuthRequired(result.provider, result.message))
                        return@launch
                    }
                    is FileOperationResult.PermissionRequired -> {
                        Timber.i("BrowseDeleteManager: permission required (Android 11+ scoped storage)")
                        sendEvent(BrowseEvent.PermissionRequired(result.pendingIntent))
                        setIgnoringFileChanges(false)
                        setLoading(false)
                        return@launch
                    }
                }
            }

            // --- Delete directories ---
            if (dirItems.isNotEmpty()) {
                Timber.d("BrowseDeleteManager: deleting ${dirItems.size} directories")
                val dirResult = deleteDirectoriesUseCase(dirItems)
                dirResult
                    .onSuccess { count ->
                        totalFileCount += count
                        Timber.i("BrowseDeleteManager: deleted $count entries from ${dirItems.size} directories")
                    }
                    .onFailure { e ->
                        dirDeleteError = context.getString(R.string.error_reason_unknown)
                        Timber.e(e, "BrowseDeleteManager: failed to delete directories")
                    }
            }

            // --- Update UI ---
            clearSelection()
            removeFiles(selectedPaths)

            scope.launch {
                delay(200)
                setIgnoringFileChanges(false)
            }

            if (dirDeleteError != null) {
                val headlineRes = if (totalFileCount > 0) {
                    R.string.error_partial_success
                } else {
                    R.string.error_delete
                }
                sendEvent(
                    BrowseEvent.ShowError(
                        message = context.getString(headlineRes),
                        details = dirDeleteError
                    )
                )
            } else {
                val msg = context.getString(R.string.deleted_n_files, totalFileCount.coerceAtLeast(
                    if (dirItems.isNotEmpty() && filesToDelete.isEmpty()) dirItems.size else totalFileCount
                ))
                sendEvent(BrowseEvent.ShowMessage(msg))
            }

            setLoading(false)
        }
    }

    /**
     * Android 11+ callback: user granted batch-delete permission via system dialog.
     * The system has already physically deleted the files; we only need to update UI state.
     */
    fun onDeletePermissionGranted() {
        Timber.i("BrowseDeleteManager.onDeletePermissionGranted: called")
        val selectedPaths = stateFlow.value.selectedFiles.toList()
        val currentFiles = stateFlow.value.mediaFiles.map { it.path }.toSet()
        val filesToRemove = selectedPaths.filter { it in currentFiles }
        val alreadyRemoved = selectedPaths.size - filesToRemove.size

        Timber.i("BrowseDeleteManager: ${selectedPaths.size} selected, $alreadyRemoved already removed, removing ${filesToRemove.size}")

        if (alreadyRemoved > 0) {
            Timber.w("BrowseDeleteManager: state inconsistency — $alreadyRemoved files were removed before permission granted")
            sendEvent(BrowseEvent.ShowMessage(
                context.getString(R.string.warning_files_may_remain_in_source, alreadyRemoved)
            ))
        }

        if (filesToRemove.isNotEmpty()) removeFiles(filesToRemove)
        clearSelection()
        reloadFiles()

        if (alreadyRemoved == 0) {
            sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.deleted_n_files, selectedPaths.size)))
        }
    }

    /**
     * Alias: triggers [scanBySize] (Phase 1 of delete-by-size flow).
     */
    fun deleteBySize(minSizeMb: Float?, maxSizeMb: Float?) = scanBySize(minSizeMb, maxSizeMb)

    /**
     * Phase 1: scan resource for files matching size criteria and show preview dialog.
     */
    fun scanBySize(minSizeMb: Float?, maxSizeMb: Float?) {
        val resource = stateFlow.value.resource ?: return
        scope.launch(ioDispatcher) {
            Timber.d("BrowseDeleteManager.scanBySize: minSizeMb=$minSizeMb, maxSizeMb=$maxSizeMb")
            setLoading(true)
            val scanResult = try {
                deleteByFileSizeUseCase.scan(resource, maxSizeMb = maxSizeMb, minSizeMb = minSizeMb)
            } finally {
                setLoading(false)
            }
            withContext(Dispatchers.Main) {
                if (scanResult.files.isEmpty()) {
                    sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.delete_by_size_no_matches)))
                } else {
                    sendEvent(
                        BrowseEvent.ShowDeleteBySizePreview(
                            count = scanResult.files.size,
                            totalBytes = scanResult.totalBytes,
                            matchedFiles = scanResult.files
                        )
                    )
                }
            }
        }
    }

    /**
     * Phase 2: execute confirmed deletion of [files] returned by [scanBySize].
     */
    fun executeBySizeDeleteConfirmed(files: List<com.sza.fastmediasorter.domain.model.MediaFile>) {
        if (files.isEmpty()) return
        scope.launch(ioDispatcher) {
            Timber.d("BrowseDeleteManager.executeBySizeDeleteConfirmed: deleting ${files.size} files")
            when (val result = deleteByFileSizeUseCase.execute(files)) {
                is FileOperationResult.Success -> {
                    withContext(Dispatchers.Main) {
                        sendEvent(BrowseEvent.ShowMessage(
                            context.getString(R.string.deleted_n_files, result.processedCount)
                        ))
                        loadResource()
                    }
                }
                is FileOperationResult.PartialSuccess -> {
                    withContext(Dispatchers.Main) {
                        sendEvent(BrowseEvent.ShowError(
                            context.getString(R.string.error_partial_success),
                            context.getString(R.string.deleted_n_of_m_files, result.processedCount, result.processedCount + result.failedCount)
                        ))
                        loadResource()
                    }
                }
                is FileOperationResult.Failure -> {
                    Timber.e("BrowseDeleteManager.executeBySizeDeleteConfirmed: failure — ${result.error}")
                    withContext(Dispatchers.Main) {
                        sendEvent(BrowseEvent.ShowError(
                            context.getString(R.string.error_deletion_failed),
                            formatFailureDetails(result)
                        ))
                    }
                }
                is FileOperationResult.PermissionRequired -> {
                    withContext(Dispatchers.Main) {
                        sendEvent(BrowseEvent.PermissionRequired(result.pendingIntent))
                    }
                }
                is FileOperationResult.AuthenticationRequired -> {
                    withContext(Dispatchers.Main) {
                        sendEvent(BrowseEvent.CloudAuthRequired(result.provider, result.message))
                    }
                }
            }
        }
    }
}
