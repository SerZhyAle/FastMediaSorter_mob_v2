package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.SyntheticResourceIds
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages file opening and "missing file" resolution in the Browse screen.
 *
 * Responsibilities:
 * - Resolve the player index for a file and emit [BrowseEvent.NavigateToPlayer].
 * - On cache-miss, attempt to re-fetch the file from its source (SMB only for now).
 * - Merge a freshly resolved file back into the in-memory and DB cache.
 * - Persist [MediaResource.lastViewedFile] on each open.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
 */
class BrowseFileOpenManager(
    private val context: Context,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val mediaScannerFactory: MediaScannerFactory,
    private val cachedFileListRepository: CachedFileListRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val inlineStop: () -> Unit,
    private val sortFiles: (files: List<MediaFile>, mode: SortMode, forceSort: Boolean) -> List<MediaFile>,
    private val resourceId: Long
) {
    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Open [file] in the player, resolving its index in the current media list.
     *
     * In pagination mode, [approximatePosition] is passed directly to the player.
     * In standard mode, the exact index is looked up; on cache-miss an SMB re-fetch is attempted.
     */
    fun openFile(file: MediaFile, approximatePosition: Int = 0) {
        Timber.d("BrowseFileOpenManager.openFile: file=${file.name}, type=${file.type}, pos=$approximatePosition")
        // S0783: a favorited live channel materializes with the STREAM synthetic resource id and its URL
        // as the path. It is not a browsable file, so route it straight to the stream player instead of
        // the file-index lookup + file viewer below.
        if (file.resourceId == SyntheticResourceIds.STREAM) {
            inlineStop()
            sendEvent(BrowseEvent.OpenStreamPlayer(file.path))
            return
        }
        val resource = stateFlow.value.resource ?: return

        val index = run {
            val foundIndex = stateFlow.value.mediaFiles.indexOfFirst { it.path == file.path }
            if (foundIndex == -1) {
                Timber.w("BrowseFileOpenManager.openFile: cache miss for ${file.path}, attempting fallback")
                if (!tryResolveMissingFile(file, approximatePosition)) {
                    // Keep the primary copy short; the missing file path only belongs in the optional details surface.
                    sendEvent(BrowseEvent.ShowError(context.getString(R.string.error_file_not_found), file.path))
                }
                return
            }
            foundIndex
        }

        // S1001: targeted single-column write - a full-entity update from this (possibly stale)
        // in-memory copy clobbers statistics written by BrowseMetadataManager during load.
        updateState { it.copy(resource = it.resource?.copy(lastViewedFile = file.path) ?: it.resource) }

        scope.launch(ioDispatcher) {
            updateResourceUseCase.saveLastViewedFile(resource.id, file.path)
            Timber.d("BrowseFileOpenManager.openFile: saved lastViewedFile=${file.path}")
        }

        inlineStop()
        sendEvent(BrowseEvent.NavigateToPlayer(file.path, index))
    }

    /**
     * S0189: open [path] in PlayerActivity with edit mode pre-activated.
     * Used by [BrowseTextNoteCreateManager] immediately after a text note is created.
     */
    fun openTextNoteInEditor(path: String, resourceId: Long) {
        inlineStop()
        sendEvent(BrowseEvent.NavigateToTextEditor(path, resourceId))
    }

    /**
     * S0191: open a freshly created blank drawing in PlayerActivity and enter draw mode.
     */
    fun openDrawingInEditor(path: String, resourceId: Long) {
        inlineStop()
        sendEvent(BrowseEvent.NavigateToDrawingEditor(path, resourceId))
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Attempt to resolve a file that is not present in the current in-memory list.
     * @return `true` if an async resolution was launched; `false` if unsupported for this resource type.
     */
    private fun tryResolveMissingFile(file: MediaFile, approximatePosition: Int): Boolean {
        val resource = stateFlow.value.resource ?: return false
        return when (resource.type) {
            ResourceType.SMB -> {
                resolveMissingSmbFile(resource, file, approximatePosition)
                true
            }
            else -> false
        }
    }

    /**
     * Re-fetch [file] from SMB and merge it back into the list before opening.
     */
    private fun resolveMissingSmbFile(resource: MediaResource, file: MediaFile, approximatePosition: Int) {
        scope.launch(ioDispatcher) {
            Timber.d("BrowseFileOpenManager.resolveMissingSmbFile: refreshing ${file.path}")
            setLoading(true)
            try {
                val scanner = mediaScannerFactory.getScanner(resource.type)
                val smbScanner = scanner as? com.sza.fastmediasorter.data.network.SmbMediaScanner
                if (smbScanner == null) {
                    Timber.e("BrowseFileOpenManager.resolveMissingSmbFile: no SMB scanner for '${resource.name}'")
                    sendEvent(BrowseEvent.ShowError(context.getString(R.string.error_file_not_found), file.path))
                    return@launch
                }

                val refreshed = smbScanner.getFileByPath(
                    path = file.path,
                    supportedTypes = resource.supportedMediaTypes,
                    credentialsId = resource.credentialsId
                )

                if (refreshed != null) {
                    mergeResolvedFileAndOpen(refreshed, approximatePosition)
                } else {
                    Timber.e("BrowseFileOpenManager.resolveMissingSmbFile: remote file missing ${file.path}")
                    sendEvent(BrowseEvent.ShowError(context.getString(R.string.error_file_not_found), file.path))
                }
            } finally {
                setLoading(false)
            }
        }
    }

    /**
     * Merge [resolvedFile] into the current file list, update the cache, then open the player.
     */
    private fun mergeResolvedFileAndOpen(resolvedFile: MediaFile, approximatePosition: Int) {
        val updatedList = stateFlow.value.mediaFiles.toMutableList().apply {
            removeAll { it.path == resolvedFile.path }
            add(resolvedFile)
        }

        val sortedList = sortFiles(updatedList, stateFlow.value.sortMode, true)

        MediaFilesCacheManager.setCachedList(resourceId, sortedList)
        updateState { it.copy(mediaFiles = sortedList, totalFileCount = sortedList.size) }

        val resource = stateFlow.value.resource
        if (resource?.rememberFileList == true) {
            scope.launch(ioDispatcher) {
                try {
                    cachedFileListRepository.saveCachedFiles(resource.id, sortedList)
                } catch (e: Exception) {
                    Timber.e(e, "BrowseFileOpenManager.mergeResolvedFileAndOpen: failed to save DB cache")
                }
            }
        }

        val targetIndex = sortedList.indexOfFirst { it.path == resolvedFile.path }
        val indexForPlayer = if (targetIndex >= 0) targetIndex else approximatePosition

        inlineStop()
        sendEvent(BrowseEvent.NavigateToPlayer(resolvedFile.path, indexForPlayer))
    }
}
