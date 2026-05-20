package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.data.local.preferences.BrowseManualOrderPrefs
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Owns MANUAL-sort-mode ordering:
 *   - sortFiles() - applies saved order if mode == MANUAL, otherwise delegates.
 *   - applyManualOrder() - interleaves directories alphabetically, then files by saved index.
 *   - saveManualOrder() - persists drag-reordered paths and reapplies them to the visible list.
 *
 * The coordinator has no coroutine ownership - all calls are synchronous. It reads state via
 * the provided `stateFlow` and writes through `updateState`, matching the convention used by
 * other Browse managers.
 */
class BrowseManualOrderCoordinator(
    private val manualOrderPrefs: BrowseManualOrderPrefs,
    private val resourceId: Long,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val fallbackSort: (files: List<MediaFile>, mode: SortMode, force: Boolean) -> List<MediaFile>
) {

    fun currentDirPath(): String =
        stateFlow.value.currentPath ?: stateFlow.value.resource?.path ?: ""

    fun sortFiles(
        files: List<MediaFile>,
        mode: SortMode,
        forceSort: Boolean = false
    ): List<MediaFile> {
        if (mode == SortMode.MANUAL) {
            val savedOrder = manualOrderPrefs.loadOrder(resourceId, currentDirPath())
            if (savedOrder != null) return applyManualOrder(files, savedOrder)
        }
        return fallbackSort(files, mode, forceSort)
    }

    fun saveManualOrder(orderedPaths: List<String>) {
        val dirPath = currentDirPath()
        manualOrderPrefs.saveOrder(resourceId, dirPath, orderedPaths)
        Timber.d("BrowseManualOrderCoordinator.saveManualOrder: saved ${orderedPaths.size} paths for $dirPath")
        if (stateFlow.value.sortMode == SortMode.MANUAL) {
            val reordered = applyManualOrder(stateFlow.value.mediaFiles, orderedPaths)
            updateState { it.copy(mediaFiles = reordered) }
        }
    }

    private fun applyManualOrder(
        files: List<MediaFile>,
        orderedPaths: List<String>
    ): List<MediaFile> {
        val pathIndex = orderedPaths.withIndex().associate { (i, p) -> p to i }
        val dirs = files.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val regular = files.filter { !it.isDirectory }
            .sortedBy { pathIndex[it.path] ?: Int.MAX_VALUE }
        return dirs + regular
    }
}
