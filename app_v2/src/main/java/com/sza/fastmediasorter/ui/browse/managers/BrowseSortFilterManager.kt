package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.filelist.BrowseFileListManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.random.Random

/**
 * Manages sort mode, display mode, and file filtering in the Browse screen.
 *
 * Responsibilities:
 * - Persist sort mode changes to the Room database ([setSortMode]).
 * - Persist display mode changes to the Room database ([toggleDisplayMode]).
 * - Apply session-only file filters without touching the resource ([setFilter], [applyFilter]).
 * - Provide filter-to-list application and metadata-guard helpers.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition — IV.1).
 */
class BrowseSortFilterManager(
    private val context: Context,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val loadMediaFiles: () -> Unit,
    private val getFriendlyErrorMessage: (Throwable) -> String,
    private val getSettings: suspend () -> AppSettings,
    private val fileListManager: BrowseFileListManager,
    private val resourceId: Long,
    private val paginationThreshold: Int = DEFAULT_PAGINATION_THRESHOLD
) {
    private var randomShuffleSeed: Long = System.nanoTime()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Change the sort mode for the current resource.
     *
     * If a cached list exists and already has the required metadata, re-sorts in-memory.
     * Otherwise triggers a full rescan via [loadMediaFiles].
     * Persists the change to the database immediately (NonCancellable).
     */
    fun setSortMode(sortMode: SortMode) {
        val resource = stateFlow.value.resource ?: return

        Timber.d("BrowseSortFilterManager.setSortMode: ${stateFlow.value.sortMode} → $sortMode for '${resource.name}'")

        val updatedResource = resource.copy(sortMode = sortMode)
        updateState { it.copy(sortMode = sortMode, resource = updatedResource) }

        scope.launch(ioDispatcher) {
            withContext(NonCancellable) {
                updateResourceUseCase(updatedResource)
                Timber.d("BrowseSortFilterManager.setSortMode: saved sortMode=$sortMode for '${resource.name}'")

                val verification = getResourcesUseCase.getById(resource.id)
                Timber.d("BrowseSortFilterManager.setSortMode: verified sortMode=${verification?.sortMode}")
            }
        }

        val cachedFiles = MediaFilesCacheManager.getCachedList(resourceId)
        if (cachedFiles != null && cachedFiles.isNotEmpty()) {
            if (cachedFilesMissingMetadataForSort(cachedFiles, sortMode)) {
                Timber.d("BrowseSortFilterManager.setSortMode: cache lacks metadata for $sortMode — clearing + reload")
                MediaFilesCacheManager.clearCache(resourceId)
                loadMediaFiles()
                return
            }
            Timber.d("BrowseSortFilterManager.setSortMode: re-sorting cache (${cachedFiles.size} files)")
            setLoading(true)
            updateState { it.copy(isSorting = true) }
            scope.launch(ioDispatcher) {
                val currentFilter = stateFlow.value.filter ?: FileFilter()
                val filteredFiles = applyFilterToList(cachedFiles, currentFilter)
                val sortedFiles = sortFiles(filteredFiles, sortMode, forceSort = true)
                val sortedCache = sortFiles(cachedFiles, sortMode, forceSort = true)
                MediaFilesCacheManager.setCachedList(resourceId, sortedCache)
                // Keep isSorting = true — cleared by BrowseActivity.clearSorting()
                // after DiffUtil finishes computing the diff for the sorted list.
                updateState { it.copy(mediaFiles = sortedFiles) }
            }
        } else {
            if (cachedFiles != null && cachedFiles.isEmpty()) {
                Timber.w("BrowseSortFilterManager.setSortMode: empty cache — clearing before reload")
                MediaFilesCacheManager.clearCache(resourceId)
            }
            loadMediaFiles()
        }
    }

    /**
     * RANDOM is intentionally re-entrant from the sort dialog: every tap should reshuffle.
     */
    fun reshuffleRandom() {
        refreshRandomShuffleSeed()

        if (stateFlow.value.sortMode != SortMode.RANDOM) {
            setSortMode(SortMode.RANDOM)
            return
        }

        val cachedFiles = MediaFilesCacheManager.getCachedList(resourceId)
        if (cachedFiles != null && cachedFiles.isNotEmpty()) {
            setLoading(true)
            updateState { it.copy(isSorting = true) }
            scope.launch(ioDispatcher) {
                val currentFilter = stateFlow.value.filter ?: FileFilter()
                val filteredFiles = applyFilterToList(cachedFiles, currentFilter)
                val reshuffledFiles = reshuffleVisibleFiles(filteredFiles)
                val reshuffledCache = sortFiles(cachedFiles, SortMode.RANDOM, forceSort = true)
                MediaFilesCacheManager.setCachedList(resourceId, reshuffledCache)
                // Keep isSorting = true — cleared by BrowseActivity.clearSorting() after DiffUtil.
                updateState { it.copy(mediaFiles = reshuffledFiles) }
            }
            return
        }

        val currentFiles = stateFlow.value.mediaFiles
        if (currentFiles.isNotEmpty()) {
            setLoading(true)
            updateState { it.copy(isSorting = true) }
            scope.launch(ioDispatcher) {
                // Keep isSorting = true — cleared by BrowseActivity.clearSorting() after DiffUtil.
                updateState { it.copy(mediaFiles = reshuffleVisibleFiles(currentFiles)) }
            }
            return
        }

        loadMediaFiles()
    }

    /**
     * Toggle display mode between [DisplayMode.LIST] and [DisplayMode.GRID].
     * Audio-only resources are locked to LIST and the toggle is ignored.
     * Persists the change to the database immediately (NonCancellable).
     */
    fun toggleDisplayMode() {
        val resource = stateFlow.value.resource ?: return

        if (resource.isAudioOnly()) {
            if (stateFlow.value.displayMode != DisplayMode.LIST || resource.displayMode != DisplayMode.LIST) {
                val updatedResource = resource.copy(displayMode = DisplayMode.LIST)
                updateState { it.copy(displayMode = DisplayMode.LIST, resource = updatedResource) }
                scope.launch(ioDispatcher) {
                    withContext(NonCancellable) {
                        updateResourceUseCase(updatedResource)
                    }
                }
            }
            Timber.d("BrowseSortFilterManager.toggleDisplayMode: ignored for audio-only resource '${resource.name}'")
            return
        }

        val newMode = if (stateFlow.value.displayMode == DisplayMode.LIST) DisplayMode.GRID else DisplayMode.LIST
        val updatedResource = resource.copy(displayMode = newMode)
        updateState { it.copy(displayMode = newMode, resource = updatedResource) }

        scope.launch(ioDispatcher) {
            withContext(NonCancellable) {
                updateResourceUseCase(updatedResource)
                Timber.d("BrowseSortFilterManager.toggleDisplayMode: saved displayMode=$newMode for '${resource.name}'")
            }
        }
    }

    /**
     * Apply a session-only filter (does NOT persist to the resource).
     * Passes `null` to clear the filter.
     */
    fun setFilter(filter: FileFilter?) {
        Timber.d("BrowseSortFilterManager.setFilter: $filter")
        updateState { it.copy(filter = filter) }
        applyFilter()
    }

    /**
     * Reload and re-filter the file list using the current filter state.
     * Called on filter changes and on filter state restoration from DataStore.
     *
     * Fast path: if the in-memory cache is already populated (e.g. after a cache hit
     * during resource load) the filter is applied directly to those files without
     * triggering a new network/disk scan. This prevents a race condition where
     * restoreFilterState() fires after a cache hit and wipes the displayed list by
     * kicking off an SMB scan that may return 0 files due to transient connection issues.
     */
    fun applyFilter() {
        val resource = stateFlow.value.resource ?: return
        val filter = stateFlow.value.filter

        Timber.d("BrowseSortFilterManager.applyFilter: filter=$filter")

        // Fast path — files are already in memory (e.g. cache hit); no scan needed.
        val cachedFiles = MediaFilesCacheManager.getCachedList(resource.id)
        if (cachedFiles != null) {
            val filteredFiles = if (filter != null) {
                applyFilterToList(cachedFiles, filter).also {
                    Timber.d("BrowseSortFilterManager.applyFilter (cache fast-path): ${cachedFiles.size} → ${it.size} files")
                }
            } else cachedFiles
            updateState { it.copy(mediaFiles = filteredFiles) }
            return
        }

        // Slow path — cache is empty, trigger a full scan.
        scope.launch(ioDispatcher) {
            setLoading(true)

            val settings = getSettings()
            val sizeFilter = SizeFilter(
                imageSizeMin = settings.imageSizeMin,
                imageSizeMax = settings.imageSizeMax,
                videoSizeMin = settings.videoSizeMin,
                videoSizeMax = settings.videoSizeMax,
                audioSizeMin = settings.audioSizeMin,
                audioSizeMax = settings.audioSizeMax
            )

            getMediaFilesUseCase(resource, stateFlow.value.sortMode, sizeFilter)
                .catch { e ->
                    Timber.e(e, "BrowseSortFilterManager.applyFilter: loading failure")
                    sendEvent(BrowseEvent.ShowError(
                        message = getFriendlyErrorMessage(e),
                        details = buildString {
                            append(context.getString(R.string.error_details_error))
                            append(": ")
                            append(e.message ?: e.javaClass.simpleName)
                            append("\n\n")
                            append(context.getString(R.string.error_details_stack_trace))
                            append(":\n")
                            append(e.stackTraceToString())
                        },
                        exception = e
                    ))
                }
                .collect { files ->
                    val filteredFiles = if (filter != null) {
                        applyFilterToList(files, filter).also {
                            Timber.d("BrowseSortFilterManager.applyFilter: ${files.size} → ${it.size} files")
                        }
                    } else files
                    updateState { it.copy(mediaFiles = filteredFiles) }
                    setLoading(false)
                }
        }
    }

    /**
     * Apply [filter] predicate to [files] and return the matching subset.
     * Exposed as `internal` so [BrowseViewModel] callers (e.g. [setSortMode]) can reuse it.
     */
    fun applyFilterToList(files: List<MediaFile>, filter: FileFilter): List<MediaFile> {
        return files.filter { file ->
            val matchesName = filter.nameContains == null ||
                file.name.contains(filter.nameContains, ignoreCase = true)
            val matchesMinDate = filter.minDate == null || file.createdDate >= filter.minDate
            val matchesMaxDate = filter.maxDate == null || file.createdDate <= filter.maxDate
            val fileSizeMb = file.size / (1024f * 1024f)
            val matchesMinSize = filter.minSizeMb == null || fileSizeMb >= filter.minSizeMb
            val matchesMaxSize = filter.maxSizeMb == null || fileSizeMb <= filter.maxSizeMb
            val matchesType = filter.mediaTypes == null || filter.mediaTypes.contains(file.type)
            matchesName && matchesMinDate && matchesMaxDate && matchesMinSize && matchesMaxSize && matchesType
        }
    }

    /**
     * Sort [files] by [mode]. Skips sorting for large folders during initial load
     * (when `forceSort = false`) to improve performance.
     * Exposed so the ViewModel can delegate its own `sortFiles` wrapper here.
     */
    fun sortFiles(files: List<MediaFile>, mode: SortMode, forceSort: Boolean = false): List<MediaFile> {
        if (!forceSort && files.size > paginationThreshold) {
            Timber.d("BrowseSortFilterManager.sortFiles: large folder (${files.size}) — skipping auto-sort")
            return files
        }
        return fileListManager.sortFiles(files, mode, forceSort, randomShuffleSeed)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns `true` when [sortMode] requires metadata fields (artist, title, duration, exifDateTime)
     * that may not have been extracted during the initial scan.
     * In that case the cached list is unusable and a fresh enriched scan is needed.
     */
    private fun cachedFilesMissingMetadataForSort(
        files: List<MediaFile>,
        sortMode: SortMode
    ): Boolean {
        return when (sortMode) {
            SortMode.ARTIST_ASC, SortMode.ARTIST_DESC,
            SortMode.TITLE_ASC, SortMode.TITLE_DESC -> {
                val audioFiles = files.filter { it.type == MediaType.AUDIO }
                audioFiles.isNotEmpty() && audioFiles.all { it.artist == null && it.title == null }
            }
            SortMode.DURATION_ASC, SortMode.DURATION_DESC -> {
                val audioFiles = files.filter { it.type == MediaType.AUDIO }
                audioFiles.isNotEmpty() && audioFiles.all { it.duration == null }
            }
            SortMode.DATE_TAKEN_ASC, SortMode.DATE_TAKEN_DESC -> {
                val imageFiles = files.filter { it.type == MediaType.IMAGE || it.type == MediaType.GIF }
                imageFiles.isNotEmpty() && imageFiles.all { it.exifDateTime == null }
            }
            else -> false
        }
    }

    private fun reshuffleVisibleFiles(files: List<MediaFile>): List<MediaFile> {
        if (files.count { !it.isDirectory } < 2) return files

        val currentOrder = files.map { it.path }
        repeat(3) {
            refreshRandomShuffleSeed()
            val reshuffled = sortFiles(files, SortMode.RANDOM, forceSort = true)
            if (reshuffled.map { it.path } != currentOrder) {
                return reshuffled
            }
        }

        val directories = files.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val regularFiles = files.filter { !it.isDirectory }
        // As a last resort, rotate the current order so a repeat never looks like a no-op.
        return directories + (regularFiles.drop(1) + regularFiles.first())
    }

    private fun refreshRandomShuffleSeed() {
        // Mix monotonic time and current size so rapid consecutive taps don't replay a seed.
        randomShuffleSeed = System.nanoTime() xor stateFlow.value.mediaFiles.size.toLong() xor Random.nextLong()
    }

    companion object {
        private const val DEFAULT_PAGINATION_THRESHOLD = 500
    }
}
