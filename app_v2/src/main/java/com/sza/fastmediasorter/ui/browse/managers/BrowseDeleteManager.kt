package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.DeleteByFileSizeUseCase
import com.sza.fastmediasorter.domain.usecase.DeletePathPolicy
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferRequest
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages file and directory deletion operations in the Browse screen.
 *
 * Responsibilities:
 * - Delete selected files and directories by enqueueing a [BrowseFileTransferRequest].
 * - Handle Android 11+ batch-delete permission grant callback.
 * - Scan/delete files by size ([scanBySize], [executeBySizeDeleteConfirmed]).
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
 */
@Suppress("LongParameterList") // Mirrors the host-supplied surface one-to-one, as the sibling browse managers do.
class BrowseDeleteManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val deleteByFileSizeUseCase: DeleteByFileSizeUseCase,
    private val browseTransferCoordinator: BrowseFileTransferCoordinator,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val clearSelection: () -> Unit,
    private val removeFiles: (List<String>) -> Unit,
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
     * Delete files. Uses soft-delete (trash) when possible; hard-delete otherwise.
     * Emits [BrowseEvent.PermissionRequired] for Android 11+ scoped-storage batch ops.
     *
     * @param overridePaths When non-null, deletes exactly these paths instead of the
     *   global multiselect. Used by the per-file overflow menu so the user's existing
     *   multiselect remains untouched. When non-null, [clearSelection] is also skipped.
     */
    fun deleteSelectedFiles(overridePaths: Set<String>? = null) {
        val selectedPaths = (overridePaths ?: stateFlow.value.selectedFiles).toList()
        if (selectedPaths.isEmpty()) {
            sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.no_files_selected)))
            return
        }
        val resource = stateFlow.value.resource ?: return
        val selectedSet = selectedPaths.toSet()
        val knownFiles = stateFlow.value.mediaFiles.associateBy { it.path }
        val settings = settingsRepository.getSettings()
        scope.launch(ioDispatcher) {
            val softDelete = settings.first().useTrash &&
                DeletePathPolicy.canUseSoftDelete(selectedPaths)
            val request = BrowseFileTransferRequest(
                operationType = FileOperationType.DELETE,
                sourceResourceId = resource.id,
                sourceResourceName = resource.name,
                sourceCredentialsId = resource.credentialsId,
                currentBrowsePath = selectedPaths.firstOrNull()?.substringBeforeLast('/', ""),
                destinationPath = resource.path,
                destinationName = resource.name,
                overwriteFiles = false,
                sources = selectedPaths.map { path ->
                    val file = knownFiles[path]
                    BrowseFileTransferSource(
                        path = path,
                        displayName = file?.name ?: path.substringAfterLast('/'),
                        size = file?.size ?: 0L,
                        isDirectory = file?.isDirectory == true,
                    )
                },
                softDelete = softDelete,
            )
            when (browseTransferCoordinator.enqueueIfIdle(request)) {
                is BrowseFileTransferCoordinator.EnqueueResult.ActiveAlreadyRunning -> {
                    sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.browse_transfer_already_running)))
                }
                is BrowseFileTransferCoordinator.EnqueueResult.Enqueued -> {
                    if (selectedSet.size > 10) {
                        sendEvent(
                            BrowseEvent.ShowMessage(context.getString(R.string.deleting_n_files, selectedSet.size))
                        )
                    }
                }
            }
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

        Timber.i(
            "BrowseDeleteManager: %d selected, %d already removed, removing %d",
            selectedPaths.size,
            alreadyRemoved,
            filesToRemove.size
        )

        if (alreadyRemoved > 0) {
            Timber.w("BrowseDeleteManager: %d files removed before permission granted", alreadyRemoved)
            sendEvent(
                BrowseEvent.ShowMessage(
                    context.getString(R.string.warning_files_may_remain_in_source, alreadyRemoved)
                )
            )
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
                        sendEvent(
                            BrowseEvent.ShowMessage(
                                context.getString(R.string.deleted_n_files, result.processedCount)
                            )
                        )
                        loadResource()
                    }
                }
                is FileOperationResult.PartialSuccess -> {
                    withContext(Dispatchers.Main) {
                        sendEvent(
                            BrowseEvent.ShowError(
                                context.getString(R.string.error_partial_success),
                                context.getString(
                                    R.string.deleted_n_of_m_files,
                                    result.processedCount,
                                    result.processedCount + result.failedCount
                                )
                            )
                        )
                        loadResource()
                    }
                }
                is FileOperationResult.Failure -> {
                    Timber.e("BrowseDeleteManager.executeBySizeDeleteConfirmed: failure - ${result.error}")
                    withContext(Dispatchers.Main) {
                        sendEvent(
                            BrowseEvent.ShowError(
                                context.getString(R.string.error_deletion_failed),
                                formatFailureDetails(result)
                            )
                        )
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
