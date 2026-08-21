package com.sza.fastmediasorter.ui.browse.loading

import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages file loading operations for BrowseViewModel.
 * Handles both standard loading and pagination setup.
 */
class BrowseLoadingManager(
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val favoritesUseCase: FavoritesUseCase,
    private val resourceId: Long,
    private val viewModelScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val paginationThreshold: Int = 500
) {
    companion object {
        private const val FAVORITES_FIRST_FRAME_TARGET_MS = 120L

        // How often the scan may log its progress, and how many files must pass between logs.
        // Both guard the log, not the scan: the scan reports every file, the log every step.
        private const val PROGRESS_LOG_INTERVAL_MS = 5_000L
        private const val PROGRESS_LOG_STEP = 1_000

        // The UI progress counter is refreshed every this many files - often enough to look
        // live, rarely enough not to cost more than the scan it reports on.
        private const val PROGRESS_UI_REFRESH_STEP = 50

        // Milliseconds per second, for the files-per-second figure in the progress log.
        private const val MILLIS_PER_SECOND = 1000L
    }

    /**
     * S1311: the shape of one standard scan, bundled - the seven loose arguments put
     * [loadFilesStandard] at the detekt arity ceiling, and the single call site
     * (BrowseResourceLoadManager) reads better naming the shape once.
     */
    data class StandardScanRequest(
        val resource: MediaResource,
        val sortMode: SortMode,
        val sizeFilter: SizeFilter,
        val shouldStopScan: AtomicBoolean,
        val showHiddenFiles: Boolean,
        val currentPath: String? = null,
        val isSubfolderMode: Boolean = false,
    )

    /**
     * Callbacks for communication with ViewModel.
     */
    interface LoadingCallbacks {
        suspend fun updateLoadingProgress(progress: Int)
        suspend fun updateState(
            mediaFiles: List<MediaFile>,
            loadingProgress: Int,
            totalFileCount: Int,
            isScanCancellable: Boolean
        )
        fun setLoading(loading: Boolean)
        suspend fun handleLoadingError(resource: MediaResource, error: Throwable)
        suspend fun updateResourceMetadata(resource: MediaResource, fileCount: Int, subfolderCount: Int)
        suspend fun onFilesLoaded(resource: MediaResource, files: List<MediaFile>)
        fun startFileObserver()
        fun sortFiles(files: List<MediaFile>, sortMode: SortMode, forceSort: Boolean): List<MediaFile>
    }

    /**
     * Loads media files using standard (non-paginated) approach.
     * Scans all files with progress tracking, sorts them, caches results.
     *
     * @param request The bundled scan shape (resource, sorting, filters, stop flag, path mode)
     * @param callbacks Callbacks for communication with ViewModel
     */
    suspend fun loadFilesStandard(request: StandardScanRequest, callbacks: LoadingCallbacks) {
        Timber.d("S1311: standard scan via StandardScanRequest - restructured path entered")
        val resource = request.resource
        Timber.d(
            "BrowseLoadingManager: START loading - resource='${resource.name}' (id=${resource.id}), " +
                "type=${resource.type}, showHiddenFiles=${request.showHiddenFiles}, " +
                "currentPath=${request.currentPath}, isSubfolderMode=${request.isSubfolderMode}"
        )
        Timber.d(
            "BrowseLoadingManager: supportedTypes=${resource.supportedMediaTypes.map { it.name }}, " +
                "sortMode=${request.sortMode}"
        )

        // Enable progressive loading for network resources so the UI shows
        // an early batch (~1000 files) while the full scan continues.
        val isNetworkResource = resource.type == ResourceType.SMB ||
            resource.type == ResourceType.SFTP ||
            resource.type == ResourceType.FTP
        val useProgressiveLoading = isNetworkResource && !request.isSubfolderMode
        val flowStartTime = System.currentTimeMillis()
        val progressCallback = buildProgressCallback(request.shouldStopScan, flowStartTime, callbacks)

        Timber.d("BrowseLoadingManager: Calling GetMediaFilesUseCase (progressive=$useProgressiveLoading)...")
        // Collect all emissions - for progressive loading the flow may emit
        // an early partial batch followed by the complete list.
        var latestFiles: List<MediaFile> = emptyList()

        getMediaFilesUseCase(
            resource = resource,
            sortMode = request.sortMode,
            sizeFilter = request.sizeFilter,
            useChunkedLoading = false,
            maxFiles = Int.MAX_VALUE,
            showHiddenFiles = request.showHiddenFiles,
            onProgress = progressCallback,
            currentPath = request.currentPath,
            isSubfolderMode = request.isSubfolderMode,
            progressiveLoading = useProgressiveLoading
        )
            .catch { e ->
                val elapsed = System.currentTimeMillis() - flowStartTime
                Timber.e(e, "BrowseLoadingManager: ERROR in flow - Exception (after ${elapsed}ms)")
                callbacks.setLoading(false)
                callbacks.handleLoadingError(resource, e)
            }
            .collect { files ->
                val elapsed = System.currentTimeMillis() - flowStartTime
                Timber.d("BrowseLoadingManager: Flow COLLECT after ${elapsed}ms - ${files.size} files")
                latestFiles = files

                // Show every intermediate emission immediately so the user sees
                // files as soon as possible. Final post-processing (favorites,
                // caching, metadata update) happens after the flow completes.
                callbacks.updateState(
                    files,
                    loadingProgress = files.size,
                    totalFileCount = files.size,
                    isScanCancellable = true
                )
                callbacks.updateLoadingProgress(files.size)
            }

        finalizeLoadedFiles(request, latestFiles, flowStartTime, callbacks)
    }

    /** S1311: per-file progress reporting + throttled logging, extracted from [loadFilesStandard]. */
    private fun buildProgressCallback(
        shouldStopScan: AtomicBoolean,
        flowStartTime: Long,
        callbacks: LoadingCallbacks
    ): ScanProgressCallback = object : ScanProgressCallback {
        var lastReportedProgress = 0
        var lastLoggedProgress = 0
        var lastProgressLogTimestamp = flowStartTime

        override suspend fun onProgress(scannedCount: Int, currentFile: String?) {
            // Report progress every 50 files or on first file
            if (scannedCount - lastReportedProgress >= PROGRESS_UI_REFRESH_STEP || scannedCount == 1) {
                callbacks.updateLoadingProgress(scannedCount)
                lastReportedProgress = scannedCount
            }

            val now = System.currentTimeMillis()
            val shouldLogProgress = scannedCount > 0 && (
                scannedCount - lastLoggedProgress >= PROGRESS_LOG_STEP ||
                    now - lastProgressLogTimestamp >= PROGRESS_LOG_INTERVAL_MS
                )
            if (shouldLogProgress) {
                val elapsedMs = now - flowStartTime
                val filesPerSecond = if (elapsedMs > 0L) scannedCount * MILLIS_PER_SECOND / elapsedMs else 0L
                Timber.d(
                    "BrowseLoadingManager: Scan progress - $scannedCount files " +
                        "in ${elapsedMs}ms (~$filesPerSecond files/s)"
                )
                lastLoggedProgress = scannedCount
                lastProgressLogTimestamp = now
            }
        }

        override suspend fun onComplete(totalFiles: Int, durationMs: Long) {
            Timber.d("BrowseLoadingManager: Progress callback completed: $totalFiles files in ${durationMs}ms")
        }

        override fun shouldStop(): Boolean {
            return shouldStopScan.get()
        }
    }

    /**
     * S1311: post-flow processing - sorting, favorites, render/cache, metadata - extracted from
     * [loadFilesStandard] so the scan orchestration and the finishing work each fit a screen.
     */
    private suspend fun finalizeLoadedFiles(
        request: StandardScanRequest,
        files: List<MediaFile>,
        flowStartTime: Long,
        callbacks: LoadingCallbacks
    ) {
        val totalElapsed = System.currentTimeMillis() - flowStartTime
        Timber.d("BrowseLoadingManager: Flow COMPLETE after ${totalElapsed}ms - final batch: ${files.size} files")

        if (files.isEmpty()) {
            callbacks.updateState(
                emptyList(),
                loadingProgress = 0,
                totalFileCount = 0,
                isScanCancellable = false
            )
            callbacks.setLoading(false)
            return
        }

        // Apply sorting for large folders (GetMediaFilesUseCase skips it for performance)
        val shouldApplySort = request.isSubfolderMode || files.size > paginationThreshold
        val sortedFiles = if (shouldApplySort) {
            Timber.d(
                "BrowseLoadingManager: Applying sort (subfolderMode=%s, count=%d, sort=%s)",
                request.isSubfolderMode,
                files.size,
                request.sortMode
            )
            callbacks.sortFiles(files, request.sortMode, forceSort = true)
        } else {
            Timber.d("BrowseLoadingManager: Small folder (${files.size} files), using pre-sorted list")
            files
        }

        // Favorites lookup
        val paths = sortedFiles.map { it.path }
        val favoritesLookupStart = System.currentTimeMillis()
        val favoritesMap = favoritesUseCase.getFavoritesForPaths(paths)
        val favoritesLookupDuration = System.currentTimeMillis() - favoritesLookupStart
        val hasFavoriteFlags = favoritesMap.values.any { it }

        val finalFiles = if (hasFavoriteFlags) {
            sortedFiles.map { file ->
                if (favoritesMap[file.path] == true) file.copy(isFavorite = true) else file
            }
        } else {
            sortedFiles
        }

        renderAndCache(sortedFiles, finalFiles, hasFavoriteFlags, favoritesLookupDuration, callbacks)

        Timber.d("BrowseLoadingManager: COMPLETE - ${finalFiles.size} files loaded and displayed")

        val subfolderCount = countDiscoveredSubfolders(request.resource, sortedFiles)
        Timber.d("BrowseLoadingManager: Discovered $subfolderCount subfolders during scan")

        callbacks.updateResourceMetadata(request.resource, sortedFiles.size, subfolderCount)
        callbacks.onFilesLoaded(request.resource, finalFiles)
        callbacks.startFileObserver()
    }

    /**
     * S1311: the single- vs two-phase render decision, extracted from [finalizeLoadedFiles].
     * A slow favorites lookup renders the sorted list first and lets the favorite flags land in
     * a second pass, so the first frame never waits on the database.
     */
    private suspend fun renderAndCache(
        sortedFiles: List<MediaFile>,
        finalFiles: List<MediaFile>,
        hasFavoriteFlags: Boolean,
        favoritesLookupDuration: Long,
        callbacks: LoadingCallbacks
    ) {
        val shouldUseTwoPhaseFallback =
            favoritesLookupDuration > FAVORITES_FIRST_FRAME_TARGET_MS && sortedFiles.isNotEmpty()

        if (!shouldUseTwoPhaseFallback) {
            Timber.d(
                "BrowseLoadingManager: Single-phase render " +
                    "(latency=${favoritesLookupDuration}ms, files=${finalFiles.size})"
            )
            callbacks.updateState(
                finalFiles,
                loadingProgress = 0,
                totalFileCount = finalFiles.size,
                isScanCancellable = false
            )
            callbacks.setLoading(false)
            MediaFilesCacheManager.setCachedList(resourceId, finalFiles)
        } else {
            Timber.d(
                "BrowseLoadingManager: Batch favorites latency ${favoritesLookupDuration}ms " +
                    "exceeds target, two-phase fallback"
            )
            callbacks.updateState(
                sortedFiles,
                loadingProgress = 0,
                totalFileCount = sortedFiles.size,
                isScanCancellable = false
            )
            callbacks.setLoading(false)

            viewModelScope.launch(ioDispatcher) {
                MediaFilesCacheManager.setCachedList(resourceId, finalFiles)
                if (hasFavoriteFlags) {
                    callbacks.updateState(
                        finalFiles,
                        loadingProgress = 0,
                        totalFileCount = finalFiles.size,
                        isScanCancellable = false
                    )
                }
            }
        }
    }

    /** S1311: count the subdirectories the scan surfaced, extracted from [finalizeLoadedFiles]. */
    private fun countDiscoveredSubfolders(resource: MediaResource, sortedFiles: List<MediaFile>): Int {
        val rootPath = resource.path.trim().trimEnd('/')
        val discoveredDirs = mutableSetOf<String>()
        for (file in sortedFiles) {
            val filePath = file.path.trim().trimEnd('/')
            if (file.isDirectory) discoveredDirs.add(filePath)
            val parentIdx = filePath.lastIndexOf('/')
            if (parentIdx > 0) {
                val parent = filePath.substring(0, parentIdx).trimEnd('/')
                if (isDiscoveredSubfolder(parent, rootPath)) {
                    discoveredDirs.add(parent)
                }
            }
        }
        return discoveredDirs.size
    }

    /** S1311: the subfolder-membership guard, decomposed from one four-atom condition. */
    private fun isDiscoveredSubfolder(parent: String, rootPath: String): Boolean {
        if (parent.isBlank() || parent == rootPath) return false
        return rootPath.isBlank() || parent.startsWith("$rootPath/")
    }
}
