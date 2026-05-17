package com.sza.fastmediasorter.ui.browse.loading

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.data.paging.MediaFilesPagingSource
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages file loading operations for BrowseViewModel.
 * Handles both standard loading and pagination setup.
 */
class BrowseLoadingManager(
    private val mediaScannerFactory: MediaScannerFactory,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val favoritesUseCase: FavoritesUseCase,
    private val resourceId: Long,
    private val viewModelScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val pageSize: Int = 50,
    private val paginationThreshold: Int = 500
) {
    companion object {
        private const val FAVORITES_FIRST_FRAME_TARGET_MS = 120L
    }

    /**
     * Callbacks for communication with ViewModel.
     */
    interface LoadingCallbacks {
        suspend fun updateLoadingProgress(progress: Int)
        suspend fun updateState(mediaFiles: List<MediaFile>, usePagination: Boolean, loadingProgress: Int, totalFileCount: Int, isScanCancellable: Boolean)
        fun setLoading(loading: Boolean)
        suspend fun handleLoadingError(resource: MediaResource, error: Throwable)
        suspend fun updateResourceMetadata(resource: MediaResource, fileCount: Int, subfolderCount: Int)
        suspend fun onFilesLoaded(resource: MediaResource, files: List<MediaFile>)
        fun startFileObserver()
        fun sortFiles(files: List<MediaFile>, sortMode: SortMode, forceSort: Boolean): List<MediaFile>
    }
    
    /**
     * Sets up pagination for large folders using Paging3 library.
     * Creates a Pager with MediaFilesPagingSource for efficient chunked loading.
     * 
     * @param resource The resource to load files from
     * @param sortMode Current sort mode for file ordering
     * @param sizeFilter Size filters for different media types
     * @return Flow<PagingData<MediaFile>> that emits paginated data
     */
    fun setupPagination(
        resource: MediaResource,
        sortMode: SortMode,
        sizeFilter: SizeFilter
    ): Flow<PagingData<MediaFile>> {
        Timber.d("BrowseLoadingManager: Setting up pagination for resource='${resource.name}', sortMode=$sortMode")
        
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                prefetchDistance = 15,
                enablePlaceholders = false,
                initialLoadSize = pageSize * 2 // Load 2 pages initially
            ),
            pagingSourceFactory = {
                MediaFilesPagingSource(
                    resource = resource,
                    sortMode = sortMode,
                    sizeFilter = sizeFilter,
                    mediaScannerFactory = mediaScannerFactory
                )
            }
        ).flow.cachedIn(viewModelScope)
    }
    
    /**
     * Loads media files using standard (non-paginated) approach.
     * Scans all files with progress tracking, sorts them, caches results.
     * 
     * @param resource The resource to load files from
     * @param sortMode Current sort mode for file ordering
     * @param sizeFilter Size filters for different media types
     * @param shouldStopScan Flag to gracefully stop scanning
     * @param progressJob Job to cancel when loading completes
     * @param showHiddenFiles Whether to show hidden files
     * @param currentPath Current subfolder path (null for root)
     * @param isSubfolderMode Whether subfolder navigation mode is active
     * @param callbacks Callbacks for communication with ViewModel
     */
    suspend fun loadFilesStandard(
        resource: MediaResource,
        sortMode: SortMode,
        sizeFilter: SizeFilter,
        shouldStopScan: AtomicBoolean,
        progressJob: Job?,
        showHiddenFiles: Boolean,
        currentPath: String? = null,
        isSubfolderMode: Boolean = false,
        callbacks: LoadingCallbacks
    ) {
        Timber.d("BrowseLoadingManager: START loading - resource='${resource.name}' (id=${resource.id}), type=${resource.type}, showHiddenFiles=$showHiddenFiles, currentPath=$currentPath, isSubfolderMode=$isSubfolderMode")
        Timber.d("BrowseLoadingManager: supportedTypes=${resource.supportedMediaTypes.map { it.name }}, sortMode=$sortMode")
        
        // Enable progressive loading for network resources so the UI shows
        // an early batch (~1000 files) while the full scan continues.
        val isNetworkResource = resource.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB
                || resource.type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP
                || resource.type == com.sza.fastmediasorter.domain.model.ResourceType.FTP
        val useProgressiveLoading = isNetworkResource && !isSubfolderMode
        val flowStartTime = System.currentTimeMillis()
        val progressLogIntervalMs = 5_000L
        val progressLogStep = 1_000
        
        // Progress callback to update UI every 50 files for better performance
        var lastReportedProgress = 0
        var lastLoggedProgress = 0
        var lastProgressLogTimestamp = flowStartTime
        val progressCallback = object : ScanProgressCallback {
            override suspend fun onProgress(scannedCount: Int, currentFile: String?) {
                // Report progress every 50 files or on first file
                if (scannedCount - lastReportedProgress >= 50 || scannedCount == 1) {
                    callbacks.updateLoadingProgress(scannedCount)
                    lastReportedProgress = scannedCount
                }

                val now = System.currentTimeMillis()
                val shouldLogProgress = scannedCount > 0 && (
                    scannedCount - lastLoggedProgress >= progressLogStep ||
                        now - lastProgressLogTimestamp >= progressLogIntervalMs
                    )
                if (shouldLogProgress) {
                    val elapsedMs = now - flowStartTime
                    val filesPerSecond = if (elapsedMs > 0L) scannedCount * 1000L / elapsedMs else 0L
                    Timber.d(
                        "BrowseLoadingManager: Scan progress - $scannedCount files in ${elapsedMs}ms (~${filesPerSecond} files/s)"
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
        
        Timber.d("BrowseLoadingManager: Calling GetMediaFilesUseCase (progressive=$useProgressiveLoading)...")
        // Collect all emissions — for progressive loading the flow may emit
        // an early partial batch followed by the complete list.
        var latestFiles: List<MediaFile> = emptyList()
        
        getMediaFilesUseCase(
            resource = resource,
            sortMode = sortMode,
            sizeFilter = sizeFilter,
            useChunkedLoading = false,
            maxFiles = Int.MAX_VALUE,
            showHiddenFiles = showHiddenFiles,
            onProgress = progressCallback,
            currentPath = currentPath,
            isSubfolderMode = isSubfolderMode,
            progressiveLoading = useProgressiveLoading
        )
            .catch { e ->
                Timber.e(e, "BrowseLoadingManager: ERROR in flow - Exception (after ${System.currentTimeMillis() - flowStartTime}ms)")
                progressJob?.cancel()
                callbacks.setLoading(false)
                callbacks.handleLoadingError(resource, e)
            }
            .collect { files ->
                val elapsed = System.currentTimeMillis() - flowStartTime
                Timber.d("BrowseLoadingManager: Flow COLLECT after ${elapsed}ms — ${files.size} files")
                latestFiles = files
                
                // Show every intermediate emission immediately so the user sees
                // files as soon as possible. Final post-processing (favorites,
                // caching, metadata update) happens after the flow completes.
                callbacks.updateState(files, false, files.size, files.size, true)
                callbacks.updateLoadingProgress(files.size)
            }
        
        // --- Flow completed — final processing on the last emission ---
        val files = latestFiles
        val totalElapsed = System.currentTimeMillis() - flowStartTime
        Timber.d("BrowseLoadingManager: Flow COMPLETE after ${totalElapsed}ms — final batch: ${files.size} files")
        
        if (files.isEmpty()) {
            callbacks.updateState(emptyList(), false, 0, 0, false)
            progressJob?.cancel()
            callbacks.setLoading(false)
            return
        }
        
        // Apply sorting for large folders (GetMediaFilesUseCase skips it for performance)
        val sortedFiles = if (files.size > paginationThreshold) {
            Timber.d("BrowseLoadingManager: Large folder (${files.size} files), applying sort: $sortMode")
            callbacks.sortFiles(files, sortMode, forceSort = true)
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

        val shouldUseTwoPhaseFallback = favoritesLookupDuration > FAVORITES_FIRST_FRAME_TARGET_MS && sortedFiles.isNotEmpty()

        if (!shouldUseTwoPhaseFallback) {
            Timber.d(
                "BrowseLoadingManager: Single-phase render (latency=${favoritesLookupDuration}ms, files=${finalFiles.size})"
            )
            callbacks.updateState(finalFiles, false, 0, finalFiles.size, false)
            progressJob?.cancel()
            callbacks.setLoading(false)
            MediaFilesCacheManager.setCachedList(resourceId, finalFiles)
        } else {
            Timber.d(
                "BrowseLoadingManager: Batch favorites latency ${favoritesLookupDuration}ms exceeds target, two-phase fallback"
            )
            callbacks.updateState(sortedFiles, false, 0, sortedFiles.size, false)
            progressJob?.cancel()
            callbacks.setLoading(false)

            viewModelScope.launch(ioDispatcher) {
                MediaFilesCacheManager.setCachedList(resourceId, finalFiles)
                if (hasFavoriteFlags) {
                    callbacks.updateState(finalFiles, false, 0, finalFiles.size, false)
                }
            }
        }

        Timber.d("BrowseLoadingManager: COMPLETE - ${finalFiles.size} files loaded and displayed")

        // Count subdirectories discovered during this scan
        val rootPath = resource.path.trim().trimEnd('/')
        val discoveredDirs = mutableSetOf<String>()
        for (file in sortedFiles) {
            val filePath = file.path.trim().trimEnd('/')
            if (file.isDirectory) discoveredDirs.add(filePath)
            val parentIdx = filePath.lastIndexOf('/')
            if (parentIdx > 0) {
                val parent = filePath.substring(0, parentIdx).trimEnd('/')
                if (parent.isNotBlank() && parent != rootPath &&
                    (rootPath.isBlank() || parent.startsWith("$rootPath/")))
                {
                    discoveredDirs.add(parent)
                }
            }
        }
        val subfolderCount = discoveredDirs.size
        Timber.d("BrowseLoadingManager: Discovered $subfolderCount subfolders during scan")

        callbacks.updateResourceMetadata(resource, sortedFiles.size, subfolderCount)
        callbacks.onFilesLoaded(resource, finalFiles)
        callbacks.startFileObserver()
    }
}