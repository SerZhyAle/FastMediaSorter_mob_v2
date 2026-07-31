package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.usecase.CleanupOrphanedTempFilesUseCase
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.cache.BrowseCacheManager
import com.sza.fastmediasorter.ui.browse.loading.BrowseLoadingManager
import dagger.Lazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode
import kotlin.coroutines.CoroutineContext

/**
 * Manages the main resource and media-file loading pipeline in the Browse screen.
 *
 * Responsibilities:
 * - Load [MediaResource] from DB, restore state, check DB/TTL cache ([loadResource]).
 * - Scan media files with optional pagination ([loadMediaFiles]).
 * - Delegate standard scan and pagination setup to [BrowseLoadingManager].
 * - Own [loadFilesJob], [loadResourceJob], [stopButtonTimerJob], [shouldStopScan].
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
 */
class BrowseResourceLoadManager(
    private val context: Context,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val cachedFileListRepository: CachedFileListRepository,
    private val googleDriveClient: Lazy<com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient>,
    private val dropboxClient: Lazy<com.sza.fastmediasorter.data.cloud.DropboxClient>,
    private val oneDriveClient: Lazy<com.sza.fastmediasorter.data.cloud.OneDriveRestClient>,
    private val favoritesUseCase: FavoritesUseCase,
    private val audioMetadataLoader: com.sza.fastmediasorter.core.util.AudioMetadataLoader,
    private val cleanupOrphanedTempFilesUseCase: CleanupOrphanedTempFilesUseCase,
    private val getResourcesUseCase: com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase,
    private val remoteSourceGate: com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate,
    private val cacheManager: BrowseCacheManager,
    private val loadingManager: BrowseLoadingManager,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val exceptionHandler: CoroutineContext,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val isLoading: () -> Boolean,
    private val getSettings: suspend () -> AppSettings,
    private val resourceId: Long,
    private val skipAvailabilityCheck: Boolean,
    private val paginationThreshold: Int,
    // - Job-reference setters (fields owned by BrowseViewModel) -
    private val setLoadFilesJobRef: (Job?) -> Unit,
    private val setLoadResourceJobRef: (Job?) -> Unit,
    private val setStopButtonTimerJobRef: (Job?) -> Unit,
    private val shouldStopScanRef: AtomicBoolean,
    // - Cross-manager callbacks -
    private val loadFavorites: () -> Unit,
    private val onFilesLoadedSaveAndEnrich: suspend (MediaResource, List<MediaFile>) -> Unit,
    private val onHandleLoadingError: (MediaResource, Throwable) -> Unit,
    private val schedulePlayerWarmup: suspend (List<MediaFile>) -> Unit,
    private val updateResourceMetadata: suspend (MediaResource, Int, Int) -> Unit,
    private val startFileObserver: () -> Unit,
    private val sortFiles: (List<MediaFile>, SortMode, Boolean) -> List<MediaFile>
) {
    private var currentScanJob: Job? = null
    private val resolvedGoogleDriveClient by lazy(LazyThreadSafetyMode.NONE) { googleDriveClient.get() }
    private val resolvedDropboxClient by lazy(LazyThreadSafetyMode.NONE) { dropboxClient.get() }
    private val resolvedOneDriveClient by lazy(LazyThreadSafetyMode.NONE) { oneDriveClient.get() }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Load (or restore from cache) the resource identified by [resourceId].
     * Calls [loadMediaFiles] internally when a fresh scan is needed.
     */
    fun loadResource(forceRescan: Boolean = false) {
        Timber.d("BrowseResourceLoadManager.loadResource: START - resourceId=$resourceId")
        val job = scope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)

            if (resourceId == -100L) { // FAVORITES_RESOURCE_ID
                Timber.d("BrowseResourceLoadManager.loadResource: Favorites resource")
                val favoritesResource = MediaResource(
                    id = -100L, name = "Favorites", path = "Favorites",
                    type = ResourceType.LOCAL, isAvailable = true,
                    fileCount = 0, isWritable = false
                )
                updateState {
                    it.copy(
                        resource = favoritesResource,
                        sortMode = SortMode.DATE_DESC,
                        displayMode = DisplayMode.LIST,
                        isCloudResource = false,
                        filter = null
                    )
                }
                loadFavorites()
                return@launch
            }

            val resource = getResourcesUseCase.getById(resourceId)
            if (resource == null) {
                Timber.e("BrowseResourceLoadManager.loadResource: resource not found for id=$resourceId")
                sendEvent(BrowseEvent.ShowError(context.getString(R.string.resource_not_found)))
                setLoading(false)
                return@launch
            }

            // S0391: a disabled source is inert - turn back before any scan/auth/Glide load even if
            // this resource was reached outside the (already-filtered) main list (widget, deep link).
            if (!remoteSourceGate.isEnabled(resource)) {
                Timber.w("BrowseResourceLoadManager.loadResource: source disabled - not loading ${resource.name}")
                sendEvent(BrowseEvent.ShowError(context.getString(R.string.error_resource_unavailable, resource.name)))
                setLoading(false)
                return@launch
            }

            // Configure ConnectionThrottleManager thread count for this resource
            if (resource.recommendedThreads != null) {
                val key = when {
                    resource.path.startsWith("smb://") -> resource.path.substringBefore("/", resource.path)
                    resource.path.startsWith("ftp://") -> "ftp://" + resource.path.substringAfter("://").substringBefore("/")
                    resource.path.startsWith("sftp://") -> "sftp://" + resource.path.substringAfter("://").substringBefore("/")
                    else -> resource.path
                }
                ConnectionThrottleManager.setRecommendedThreads(key, resource.recommendedThreads)
                Timber.d("BrowseResourceLoadManager: configured throttle for $key × ${resource.recommendedThreads}")
            }

            Timber.d("BrowseResourceLoadManager.loadResource: '${resource.name}' type=${resource.type} fileCount=${resource.fileCount}")
            Timber.i("║ supportedMediaTypes: ${resource.supportedMediaTypes.map { it.name }}")

            val isNetworkResource = resource.type in setOf(ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP)
            if (!skipAvailabilityCheck && !isNetworkResource && resource.fileCount == 0 && !resource.isWritable) {
                Timber.w("BrowseResourceLoadManager.loadResource: unavailable resource")
                sendEvent(BrowseEvent.ShowError(
                    message = context.getString(R.string.error_resource_unavailable, resource.name),
                    details = "Resource ID: ${resource.id}\nType: ${resource.type}\nPath: ${resource.path}"
                ))
                setLoading(false)
                return@launch
            }

            val isCloudResource = resource.type == ResourceType.CLOUD
            val restoredFilter = if (resource.supportedMediaTypes.size < 7) {
                FileFilter(mediaTypes = resource.supportedMediaTypes)
            } else null

            Timber.d("BrowseResourceLoadManager.loadResource: restoredFilter=${restoredFilter != null}, sortMode=${resource.sortMode}")

            val initialSubfolderMode = resource.scanSubdirectories && resource.showSubfoldersAsItems
            val effectiveDisplayMode = if (resource.isAudioOnly()) DisplayMode.LIST else resource.displayMode
            val effectiveResource = if (resource.displayMode != effectiveDisplayMode) resource.copy(displayMode = effectiveDisplayMode) else resource

            val effectiveSortMode = if (resource.sortMode == SortMode.NAME_ASC) {
                when (resource.profile) {
                    ResourceProfile.AUDIO_LIBRARY -> SortMode.ARTIST_ASC
                    ResourceProfile.PHOTO_STORAGE -> SortMode.DATE_TAKEN_DESC
                    else -> resource.sortMode
                }
            } else resource.sortMode

            updateState {
                it.copy(
                    resource = effectiveResource,
                    sortMode = effectiveSortMode,
                    displayMode = effectiveDisplayMode,
                    isCloudResource = isCloudResource,
                    filter = restoredFilter,
                    isSubfolderMode = initialSubfolderMode,
                    currentPath = null,
                    pathStack = emptyList()
                )
            }

            if (effectiveResource != resource) updateResourceUseCase(effectiveResource)

            // Try DB-cached file list (rememberFileList mode)
            if (resource.rememberFileList && !forceRescan && !initialSubfolderMode) {
                try {
                    val dbCache = cachedFileListRepository.getCachedFiles(resource.id)
                    if (!dbCache.isNullOrEmpty()) {
                        val filtered = if (!resource.allFiles && resource.supportedMediaTypes.isNotEmpty()) {
                            dbCache.filter { it.isDirectory || resource.supportedMediaTypes.contains(it.type) }
                        } else dbCache
                        audioMetadataLoader.warmMemoryCacheForResource(resource.id)
                        MediaFilesCacheManager.setCachedList(resource.id, filtered)
                        updateState { it.copy(mediaFiles = filtered, totalFileCount = filtered.size) }
                        schedulePlayerWarmup(filtered)
                        setLoading(false)
                        updateResourceMetadata(resource, filtered.size, -1)
                        onFilesLoadedSaveAndEnrich(resource, filtered)
                        Timber.i("BrowseResourceLoadManager.loadResource: loaded ${filtered.size} from DB cache")
                        return@launch
                    }
                } catch (e: Exception) {
                    Timber.e(e, "BrowseResourceLoadManager.loadResource: DB cache load failed")
                }
            }

            // Cloud: verify authentication before starting any scan work
            if (!checkCloudAuthBeforeScan(resource)) {
                setLoading(false)
                return@launch
            }

            // TTL-based RAM cache check
            when (val cacheResult = cacheManager.checkCache(
                filter = restoredFilter ?: FileFilter(),
                lastBrowseDate = resource.lastBrowseDate
            )) {
                is BrowseCacheManager.CacheCheckResult.UseCache -> {
                    var filteredFiles = if (resource.scanSubdirectories) {
                        cacheResult.files
                    } else {
                        val rootPath = resource.path.trimEnd('/')
                        cacheResult.files.filter { file ->
                            if (file.isDirectory) return@filter false
                            file.path.trimEnd('/').substringBeforeLast('/', "").trimEnd('/') == rootPath
                        }
                    }
                    if (!resource.allFiles && resource.supportedMediaTypes.isNotEmpty()) {
                        filteredFiles = filteredFiles.filter { it.isDirectory || resource.supportedMediaTypes.contains(it.type) }
                    }
                    val reconciledFiles = try {
                        val paths = filteredFiles.filter { !it.isDirectory }.map { it.path }
                        if (paths.isEmpty()) filteredFiles
                        else {
                            val favMap = favoritesUseCase.getFavoritesForPaths(paths)
                            filteredFiles.map { if (it.isDirectory) it else it.copy(isFavorite = favMap[it.path] == true) }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "BrowseResourceLoadManager.loadResource: favorites reconcile failed")
                        filteredFiles
                    }
                    audioMetadataLoader.warmMemoryCacheForResource(resource.id)
                    updateState { it.copy(mediaFiles = reconciledFiles, totalFileCount = reconciledFiles.size) }
                    schedulePlayerWarmup(reconciledFiles)
                    setLoading(false)
                    updateResourceMetadata(resource, reconciledFiles.size, -1)
                    onFilesLoadedSaveAndEnrich(resource, reconciledFiles)
                    Timber.d("BrowseResourceLoadManager.loadResource: cache hit - ${reconciledFiles.size} files")
                    return@launch
                }
                is BrowseCacheManager.CacheCheckResult.Rescan ->
                    Timber.d("BrowseResourceLoadManager.loadResource: cache rejected - ${cacheResult.reason}")
            }

            Timber.d("BrowseResourceLoadManager.loadResource: starting fresh scan")
            loadMediaFiles()
        }
        setLoadResourceJobRef(job)
    }

    /**
     * Scan the current resource's media files.
     * Cancels any in-flight scan, starts a new [loadFilesJob].
     */
    fun loadMediaFiles() {
        val resource = stateFlow.value.resource ?: return
        val showStopImmediately = resource.type == ResourceType.SMB ||
            resource.type == ResourceType.SFTP ||
            resource.type == ResourceType.FTP

        if (resource.id == -100L) { loadFavorites(); return }

        Timber.d("BrowseResourceLoadManager.loadMediaFiles: '${resource.name}' (id=${resource.id})")

        // Cancel any previous scan before starting a new one
        if (currentScanJob?.isActive == true) {
            currentScanJob?.cancel()
        }
        shouldStopScanRef.set(false)
        updateState { it.copy(loadingProgress = 0, isScanCancellable = showStopImmediately) }

        // S1301: a while(true) progress ticker used to be launched here as a sibling of filesJob. Its
        // body only wrote a captured local (leftover from removed progress logging), and cancelling
        // the scan - STOP, onStop, or a superseding navigation - never cancelled it, so every
        // cancelled scan orphaned a 2 s ticker on viewModelScope until the screen died.
        val filesJob = scope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            shouldStopScanRef.set(false)

            // Cleanup orphaned temp files (non-blocking)
            try {
                cleanupOrphanedTempFilesUseCase(resource.path).fold(
                    onSuccess = { n -> if (n > 0) Timber.i("BrowseResourceLoadManager: cleaned $n orphaned temp file(s)") },
                    onFailure = { e -> Timber.w(e, "BrowseResourceLoadManager: temp cleanup failed (non-critical)") }
                )
            } catch (e: Exception) {
                Timber.w(e, "BrowseResourceLoadManager: temp cleanup exception (non-critical)")
            }

            // Network scans can sit on large remote trees before the first visible batch arrives.
            // Expose STOP immediately there; keep the delayed button for local/cloud scans.
            val stopTimerJob = if (showStopImmediately) {
                setStopButtonTimerJobRef(null)
                Timber.d("BrowseResourceLoadManager: network scan, showing STOP button immediately")
                null
            } else launch {
                delay(5_000L)
                if (isLoading()) {
                    updateState { it.copy(isScanCancellable = true) }
                    Timber.d("BrowseResourceLoadManager: scan >5s, showing STOP button")
                }
            }
            setStopButtonTimerJobRef(stopTimerJob)

            val settings = getSettings()
            val sizeFilter = SizeFilter(
                imageSizeMin = settings.imageSizeMin, imageSizeMax = settings.imageSizeMax,
                videoSizeMin = settings.videoSizeMin, videoSizeMax = settings.videoSizeMax,
                audioSizeMin = settings.audioSizeMin, audioSizeMax = settings.audioSizeMax
            )

            val effectiveMediaTypes = if (resource.allFiles) {
                MediaType.entries.toSet()
            } else {
                resource.supportedMediaTypes.intersect(settings.getGloballyEnabledMediaTypes())
            }
            val resourceForScan = if (effectiveMediaTypes != resource.supportedMediaTypes) {
                resource.copy(supportedMediaTypes = effectiveMediaTypes)
            } else resource

            if (resource.fileCount > 0 && resource.lastBrowseDate != null) {
                updateState { it.copy(totalFileCount = resource.fileCount) }
            }

            try {
                loadMediaFilesStandard(resourceForScan, sizeFilter)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "BrowseResourceLoadManager.loadMediaFiles: error")
                loadMediaFilesStandard(resourceForScan, sizeFilter)
            } finally {
                stopTimerJob?.cancel()
            }
        }
        currentScanJob = filesJob
        setLoadFilesJobRef(filesJob)
    }

    /** Cancel [loadFilesJob] and set [shouldStopScan] (used by navigation manager). */
    fun cancelLoad(loadFilesJob: Job?) {
        loadFilesJob?.cancel()
        currentScanJob?.cancel()
        currentScanJob = null
        shouldStopScanRef.set(true)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Early auth guard - runs before any scan scaffolding is allocated.
     * Returns true if scan may proceed; false if an auth event was emitted and scan must be aborted.
     */
    private suspend fun checkCloudAuthBeforeScan(resource: MediaResource): Boolean {
        if (resource.type != ResourceType.CLOUD) return true
        return when (val provider = resource.cloudProvider) {
            CloudProvider.GOOGLE_DRIVE -> checkGoogleDriveAuth(resource, provider)
            CloudProvider.DROPBOX      -> checkDropboxAuth(resource, provider)
            CloudProvider.ONEDRIVE     -> checkOneDriveAuth(resource, provider)
            null -> {
                sendEvent(BrowseEvent.ShowError(context.getString(R.string.error_cloud_provider_not_configured)))
                false
            }
        }
    }

    private suspend fun checkGoogleDriveAuth(resource: MediaResource, provider: CloudProvider): Boolean {
        if (!resolvedGoogleDriveClient.isAuthenticated()) {
            val restored = resolvedGoogleDriveClient.tryRestoreFromStorage()
            Timber.d("BrowseResourceLoadManager: GDrive auth restore=$restored")
        }
        val probe = resolvedGoogleDriveClient.listFiles(resource.cloudFolderId ?: "root")
        if (probe is com.sza.fastmediasorter.data.cloud.CloudResult.Error && isAuthError(probe.message)) {
            sendEvent(BrowseEvent.ShowCloudAuthenticationRequired(provider))
            return false
        }
        return true
    }

    private suspend fun checkDropboxAuth(resource: MediaResource, provider: CloudProvider): Boolean {
        val credId = resource.credentialsId
        if (credId != null && resolvedDropboxClient.tryRestoreForAccount(credId)) return true
        if (resolvedDropboxClient.isAuthenticated()) return true
        Timber.w("BrowseResourceLoadManager: Dropbox not authenticated - emitting auth required")
        sendEvent(BrowseEvent.ShowCloudAuthenticationRequired(provider))
        return false
    }

    private fun checkOneDriveAuth(resource: MediaResource, provider: CloudProvider): Boolean {
        if (resolvedOneDriveClient.isAuthenticated()) return true
        Timber.w("BrowseResourceLoadManager: OneDrive not authenticated - emitting auth required")
        sendEvent(BrowseEvent.ShowCloudAuthenticationRequired(provider))
        return false
    }

    private fun isAuthError(message: String) =
        message.contains("401", ignoreCase = true) ||
        message.contains("unauthorized", ignoreCase = true) ||
        message.contains("authentication", ignoreCase = true) ||
        message.contains("not authenticated", ignoreCase = true)

    private suspend fun loadMediaFilesStandard(
        resource: MediaResource,
        sizeFilter: SizeFilter
    ) {
        val settings = getSettings()
        val showHiddenFiles = settings.showHiddenFiles || resource.showHiddenFiles

        val callbacks = object : BrowseLoadingManager.LoadingCallbacks {
            override suspend fun updateLoadingProgress(progress: Int) {
                updateState { it.copy(loadingProgress = progress) }
            }

            override suspend fun updateState(
                mediaFiles: List<MediaFile>,
                loadingProgress: Int,
                totalFileCount: Int,
                isScanCancellable: Boolean
            ) {
                this@BrowseResourceLoadManager.updateState {
                    it.copy(
                        mediaFiles = mediaFiles,
                        loadingProgress = loadingProgress,
                        totalFileCount = totalFileCount,
                        isScanCancellable = isScanCancellable
                    )
                }
                schedulePlayerWarmup(mediaFiles)
            }

            override fun setLoading(loading: Boolean) = this@BrowseResourceLoadManager.setLoading(loading)

            override suspend fun handleLoadingError(resource: MediaResource, error: Throwable) =
                onHandleLoadingError(resource, error)

            override suspend fun updateResourceMetadata(resource: MediaResource, fileCount: Int, subfolderCount: Int) =
                this@BrowseResourceLoadManager.updateResourceMetadata(resource, fileCount, subfolderCount)

            override suspend fun onFilesLoaded(resource: MediaResource, files: List<MediaFile>) =
                onFilesLoadedSaveAndEnrich(resource, files)

            override fun startFileObserver() = this@BrowseResourceLoadManager.startFileObserver()

            override fun sortFiles(files: List<MediaFile>, sortMode: SortMode, forceSort: Boolean): List<MediaFile> =
                this@BrowseResourceLoadManager.sortFiles(files, sortMode, forceSort)
        }

        val currentState = stateFlow.value
        loadingManager.loadFilesStandard(
            resource = resource,
            sortMode = currentState.sortMode,
            sizeFilter = sizeFilter,
            shouldStopScan = shouldStopScanRef,
            showHiddenFiles = showHiddenFiles,
            currentPath = currentState.currentPath,
            isSubfolderMode = currentState.isSubfolderMode,
            callbacks = callbacks
        )
    }
}
