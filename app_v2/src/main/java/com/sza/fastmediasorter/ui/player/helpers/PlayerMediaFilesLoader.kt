package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.SyntheticResourceIds
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.IsShareTargetEnabledUseCase
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.domain.usecase.streams.GetStreamSourceByUrlUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns initial resource resolution, settings collection, and media-file list loading for
 * [PlayerViewModel]. Extracted in Wave 4.1 of the giant-file decomposition so the VM can
 * stay focused on orchestration/state shape rather than the 300-line loading pipeline.
 *
 * Lifetime: bound to `viewModelScope` supplied by the VM. `cancelLoading()` cancels the
 * active load coroutine without tearing down the coordinator itself.
 */
// Copier injection lets the unit test model a corrupted MediaFile instance without
// having to manufacture an illegal Kotlin object with null in a non-null field.
internal fun reconcileFavoriteFlags(
    files: List<MediaFile>,
    favoriteMap: Map<String, Boolean>,
    copyFavorite: (MediaFile, Boolean) -> MediaFile = { file, isFavorite ->
        file.copy(isFavorite = isFavorite)
    },
    onFailure: (MediaFile, Throwable) -> Unit = { _, _ -> }
): List<MediaFile> = files.map { file ->
    val favoritePath = runCatching { file.path }.getOrNull()
    val isFavorite = favoritePath != null && favoriteMap[favoritePath] == true
    runCatching {
        copyFavorite(file, isFavorite)
    }.onFailure { error ->
        onFailure(file, error)
    }.getOrElse {
        file
    }
}

internal fun describeMediaFileForLog(file: MediaFile): String =
    runCatching { file.path }.getOrNull()
        ?: runCatching { file.name }.getOrNull()
        ?: "<unknown>"

class PlayerMediaFilesLoader(
    private val context: Context,
    private val scope: CoroutineScope,
    private val resourceId: Long,
    private val initialFilePath: String?,
    private val initialIndex: Int,
    private val skipAvailabilityCheck: Boolean,
    private val resumeIsPlaying: Boolean?,
    private val shuffleOnStart: Boolean,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val settingsRepository: SettingsRepository,
    private val favoritesUseCase: FavoritesUseCase,
    private val googleDriveClient: GoogleDriveRestClient,
    private val cachedFileListRepository: CachedFileListRepository,
    private val textNoteStagingRegistry: LocalStagingRegistry,
    private val stateFlow: StateFlow<PlayerViewModel.PlayerState>,
    private val updateState: ((PlayerViewModel.PlayerState) -> PlayerViewModel.PlayerState) -> Unit,
    private val sendEvent: (PlayerViewModel.PlayerEvent) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val stereoCoordinator: PlayerStereoModeCoordinator,
    private val isShareTargetEnabled: IsShareTargetEnabledUseCase,
    private val getStreamSourceByUrlUseCase: GetStreamSourceByUrlUseCase,
    // S0640: snapshot the saved stream catalog so top-panel prev/next move between channels.
    private val observeStreamSourcesUseCase: ObserveStreamSourcesUseCase,
) {

    private var loadingJob: Job? = null

    // Tracks whether the launch-time initialFilePath was found to be absent from the
    // filesystem. Once set, excludes the path from cache-scope checks and prevents
    // repeated "cache scope mismatch" cycles within the same ViewModel session.
    private var initialFilePathIsStale = false

    fun loadSettings() {
        scope.launch {
            try {
                settingsRepository.getSettings().collect { settings ->
                    val resource = stateFlow.value.resource

                    // S0241: per-user VR forced-format overrides removed. PlayerStereoModeCoordinator
                    // now relies on detected stereo modes alone (no applySettings call here).

                    // Determine showCommandPanel:
                    // Priority: runtime session value → initial load via same logic as loadMediaFiles().
                    // IMPORTANT: Runtime value (files.isNotEmpty) is checked FIRST so that
                    // toggleCommandPanel() changes are never overridden by subsequent settings emissions.
                    // On initial load (files empty) we apply the same special logic as loadMediaFiles():
                    // resource explicit false is NOT honoured when global default is true.
                    val showCommandPanel = when {
                        stateFlow.value.files.isNotEmpty() -> stateFlow.value.showCommandPanel
                        resource?.showCommandPanel == null -> settings.defaultShowCommandPanel
                        resource.showCommandPanel == false && settings.defaultShowCommandPanel -> settings.defaultShowCommandPanel
                        else -> resource.showCommandPanel
                    }

                    Timber.d(
                        "TOUCH_ZONE_DEBUG: loadSettings - resource.showCommandPanel=%s, " +
                            "settings.defaultShowCommandPanel=%s, filesLoaded=%s, RESULT showCommandPanel=%s",
                        resource?.showCommandPanel,
                        settings.defaultShowCommandPanel,
                        stateFlow.value.files.isNotEmpty(),
                        showCommandPanel
                    )
                    Timber.d(
                        "PlayerViewModel.loadSettings: enableTranslation=%s, enableOcr=%s",
                        settings.enableTranslation,
                        settings.enableOcr
                    )

                    updateState {
                        it.copy(
                            showCommandPanel = showCommandPanel,
                            showSmallControls = settings.showSmallControls,
                            useCompactElements = settings.useCompactElements,
                            allowRename = settings.allowRename,
                            allowDelete = settings.allowDelete,
                            enableCopying = settings.enableCopying,
                            enableMoving = settings.enableMoving,
                            enableTranslation = settings.enableTranslation,
                            enableOcr = settings.enableOcr,
                            enableGoogleLens = isShareTargetEnabled("lens", settings),
                            slideShowInterval = settings.slideshowInterval * 1000L,
                            slideshowMusicUri = settings.slideshowMusicUri,
                            slideshowMusicResourceId = settings.slideshowMusicResourceId,
                            enableSlideshowBackgroundMusic = settings.enableSlideshowBackgroundMusic,
                            playToEndInSlideshow = settings.playToEndInSlideshow,
                            enablePhotosDuringAudio = settings.enablePhotosDuringAudio,
                            audioBackgroundPhotosResourceId = settings.audioBackgroundPhotosResourceId,
                            enablePersistentAudioPlayback = settings.enablePersistentAudioPlayback,
                            backgroundAudioExitBehavior = settings.backgroundAudioExitBehavior,
                            showNowPlayingPanel = settings.showNowPlayingPanel,
                            showBlackScreenButton = settings.showBlackScreenButton
                        )
                    }
                }
            } catch (e: CancellationException) {
                Timber.d("PlayerViewModel.loadSettings: Cancelled (normal during destroy)")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error loading settings")
            }
        }
    }

    fun loadMediaFiles() {
        loadingJob = scope.launch {
            setLoading(true)
            try {
                // Save current file path to restore position after reload
                val currentFilePath = stateFlow.value.currentFile?.path
                // Captured before reload for use as nearest-by-order fallback when the target file is gone.
                val currentIndexBeforeReload = stateFlow.value.currentIndex

                // S0591: synthetic resources have no DB row, so resolve them by their sentinel id.
                // FAVORITES and STREAM must stay numerically distinct - a stream sharing the Favorites
                // id would be materialized as the Favorites resource and inherit its favorite-toggle and
                // per-file credential behavior downstream (every `resource.id == FAVORITES` branch).
                val resource = when (resourceId) {
                    SyntheticResourceIds.FAVORITES -> MediaResource(
                        id = SyntheticResourceIds.FAVORITES,
                        name = "Favorites",
                        path = "Favorites",
                        type = ResourceType.LOCAL,
                        isAvailable = true,
                        fileCount = 0,
                        isWritable = false,
                        supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.GIF)
                    )
                    SyntheticResourceIds.STREAM -> MediaResource(
                        id = SyntheticResourceIds.STREAM,
                        name = "Stream",
                        path = "Stream",
                        // Per-file scheme classification (determineResourceType over the URL) drives stream
                        // routing; this neutral type only feeds the non-URL fallback, which never applies here.
                        type = ResourceType.LOCAL,
                        isAvailable = true,
                        fileCount = 0,
                        isWritable = false,
                        supportedMediaTypes = setOf(MediaType.VIDEO)
                    )
                    else -> getResourcesUseCase.getById(resourceId)
                }

                if (resource == null) {
                    sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.resource_not_found)))
                    sendEvent(PlayerViewModel.PlayerEvent.FinishActivity)
                    setLoading(false)
                    return@launch
                }

                // S0189: short-circuit for staged text notes. The newly-created note lives in the local
                // Downloads/FastMediaSorter/notes staging directory while its target resource is a
                // network/cloud source (SMB/FTP/SFTP/Cloud). Running the resource's scanner over the
                // local staging path returns 0 files (e.g. SmbMediaScanner: "Invalid SMB path format")
                // and the activity would then exit with "No media files found". Build a single-file
                // entry directly from the staged file so the editor opens with one writable item.
                val stagedNote = initialFilePath?.let { path ->
                    runCatching { textNoteStagingRegistry.lookup(java.io.File(path)) }.getOrNull()
                }
                if (stagedNote != null) {
                    val fileExists = stagedNote.localFile.exists()
                    // S0189 Phase 09: pick MediaType from StagedKind so future kinds (S0191 drawings)
                    // route into the right viewer without further changes here.
                    val stagedType = when (stagedNote.kind) {
                        com.sza.fastmediasorter.data.local.staging.StagedKind.TEXT_NOTE -> MediaType.TEXT
                        com.sza.fastmediasorter.data.local.staging.StagedKind.DRAWING -> MediaType.IMAGE
                        // S2044: unreachable - this branch is entered only for an entry found in
                        // LocalStagingRegistry, and a watch-received file is never registered there.
                        // Its staged copy is addressed by the upload worker's input Data instead.
                        com.sza.fastmediasorter.data.local.staging.StagedKind.WATCH_RECEIVED -> MediaType.BINARY_OTHER
                    }
                    val stagedFile = MediaFile(
                        name = stagedNote.intendedName,
                        path = stagedNote.localFile.absolutePath,
                        type = stagedType,
                        size = if (fileExists) stagedNote.localFile.length() else 0L,
                        createdDate = if (fileExists) stagedNote.localFile.lastModified() else System.currentTimeMillis(),
                        lastModified = if (fileExists) stagedNote.localFile.lastModified() else System.currentTimeMillis(),
                        resourceId = resource.id,
                    )
                    val currentSettings = settingsRepository.getSettings().first()
                    val showCommandPanel = when {
                        resource.showCommandPanel == null -> currentSettings.defaultShowCommandPanel
                        resource.showCommandPanel == false && currentSettings.defaultShowCommandPanel -> currentSettings.defaultShowCommandPanel
                        else -> resource.showCommandPanel
                    }
                    updateState {
                        it.copy(
                            files = listOf(stagedFile),
                            currentIndex = 0,
                            resource = resource,
                            slideShowInterval = resource.slideshowInterval * 1000L,
                            showCommandPanel = showCommandPanel,
                            isSlideShowActive = false,
                            isPaused = true,
                        )
                    }
                    setLoading(false)
                    return@launch
                }

                // S0565: an internet stream has no DB resource to scan. When the player is launched
                // with a stream URL as the initial path (from the Трансляции screen against the
                // synthetic resource id), play it directly as a one-item list instead of running a
                // file scanner over a non-filesystem path. determineResourceType() then classifies the
                // scheme to a stream ResourceType and routes it to the stream playback helper.
                val streamPath = initialFilePath?.takeIf {
                    it.startsWith("http://") || it.startsWith("https://") || it.startsWith("rtsp://")
                }
                if (streamPath != null) {
                    // S0590: show the human-readable channel name (from the stream list) as the
                    // player title instead of the raw URL segment. Resolved by URL so it works for
                    // any launch path; null title falls back to the URL-derived name downstream.
                    // S0592: derive MediaType from the catalog mediaKind so audio-only streams open on
                    // the audio branch instead of being forced to VIDEO. VIDEO/RTSP and unknown URLs
                    // (no catalog row) keep VIDEO.
                    val streamSource = getStreamSourceByUrlUseCase(streamPath)
                    val channelTitle = streamSource?.title?.takeIf { it.isNotBlank() }
                    val streamType = if (streamSource?.mediaKind == "AUDIO") MediaType.AUDIO else MediaType.VIDEO
                    val now = System.currentTimeMillis()
                    val launchStreamFile = MediaFile(
                        name = streamPath.substringAfterLast('/').substringBefore('?').ifBlank { streamPath },
                        path = streamPath,
                        type = streamType,
                        size = 0L,
                        createdDate = now,
                        lastModified = now,
                        resourceId = resource.id,
                        title = channelTitle,
                    )
                    // S0640: the top-panel prev/next buttons navigate the saved stream catalog, not a single
                    // item. Snapshot the video streams in list order (pinned, sortIndex, addedAt - the same
                    // order the Трансляции screen shows by default) so prev/next move between channels. Audio
                    // streams are excluded: they open inline, never in this fullscreen player. The currently
                    // playing url fixes the start index - on a config-change reload it is the navigated-to
                    // channel (stateFlow current file), otherwise the launch url. A launch url absent from the
                    // catalog (home-screen shortcut to a removed channel, ad-hoc url) keeps the single item.
                    val orderedVideoStreams = if (resource.id == SyntheticResourceIds.STREAM) {
                        runCatching { observeStreamSourcesUseCase().first() }
                            .getOrNull().orEmpty()
                            .filter { it.mediaKind != "AUDIO" }
                    } else {
                        emptyList()
                    }
                    val catalogFiles = orderedVideoStreams.map { entity ->
                        MediaFile(
                            name = entity.url.substringAfterLast('/').substringBefore('?').ifBlank { entity.url },
                            path = entity.url,
                            type = MediaType.VIDEO,
                            size = 0L,
                            createdDate = now,
                            lastModified = now,
                            resourceId = resource.id,
                            title = entity.title.takeIf { it.isNotBlank() },
                        )
                    }
                    val targetUrl = currentFilePath?.takeIf {
                        it.startsWith("http://") || it.startsWith("https://") || it.startsWith("rtsp://")
                    } ?: streamPath
                    val targetIndex = catalogFiles.indexOfFirst { it.path == targetUrl }
                    val (streamFiles, streamIndex) =
                        if (targetIndex >= 0) {
                            catalogFiles to targetIndex
                        } else {
                            listOf(launchStreamFile) to 0
                        }
                    updateState {
                        it.copy(
                            files = streamFiles,
                            currentIndex = streamIndex,
                            resource = resource,
                            isSlideShowActive = false,
                            isPaused = false,
                        )
                    }
                    setLoading(false)
                    return@launch
                }

                // Configure ConnectionThrottleManager for active resource (instead of global config in MainViewModel)
                if (resource.recommendedThreads != null) {
                    val resourceKey = when {
                        resource.path.startsWith("smb://") -> resource.path.substringBefore("/", resource.path)
                        resource.path.startsWith("ftp://") -> resource.path.substringBefore("/", resource.path.substringAfter("://"))
                            .let { "ftp://$it" }
                        resource.path.startsWith("sftp://") -> resource.path.substringBefore("/", resource.path.substringAfter("://"))
                            .let { "sftp://$it" }
                        else -> resource.path
                    }
                    ConnectionThrottleManager.setRecommendedThreads(resourceKey, resource.recommendedThreads)
                    Timber.d("PlayerViewModel: Configured ConnectionThrottleManager for $resourceKey with ${resource.recommendedThreads} threads")
                }

                // Determine actual resource type from current file path - ensures auth for subfolder browsed out of cloud root.
                val actualResourceType = stateFlow.value.currentFile?.path?.let { path ->
                    when {
                        path.startsWith("cloud://") -> ResourceType.CLOUD
                        path.startsWith("smb://") -> ResourceType.SMB
                        path.startsWith("sftp://") -> ResourceType.SFTP
                        path.startsWith("ftp://") -> ResourceType.FTP
                        else -> resource.type
                    }
                } ?: resource.type

                if (actualResourceType == ResourceType.CLOUD && resource.cloudProvider == CloudProvider.GOOGLE_DRIVE) {
                    if (!googleDriveClient.isAuthenticated()) {
                        Timber.d("PlayerViewModel: Google Drive client not authenticated, will authenticate on first API call")
                    }
                }

                // Note: Do NOT reset SMB client here! It kills active connections and causes 2+ minute delays
                // due to connection staleness checks. The connection pool already handles timeouts.

                if (!skipAvailabilityCheck && resource.fileCount == 0 && !resource.isWritable) {
                    sendEvent(
                        PlayerViewModel.PlayerEvent.ShowError(
                            context.getString(R.string.error_resource_unavailable, resource.name)
                        )
                    )
                    sendEvent(PlayerViewModel.PlayerEvent.FinishActivity)
                    setLoading(false)
                    return@launch
                }

                val settings = settingsRepository.getSettings().first()
                val sizeFilter = SizeFilter(
                    imageSizeMin = settings.imageSizeMin,
                    imageSizeMax = settings.imageSizeMax,
                    videoSizeMin = settings.videoSizeMin,
                    videoSizeMax = settings.videoSizeMax,
                    audioSizeMin = settings.audioSizeMin,
                    audioSizeMax = settings.audioSizeMax
                )

                // Load from cache (instant) - BrowseActivity already loaded and cached the list.
                val cachedFiles = MediaFilesCacheManager.getCachedList(resource.id)
                val cacheHasOnlyDirectories = cachedFiles != null && cachedFiles.isNotEmpty() && cachedFiles.all { it.isDirectory }

                val initialFileDir = initialFilePath?.let { path ->
                    val lastSlash = path.lastIndexOf('/')
                    if (lastSlash > 0) path.substring(0, lastSlash) else null
                }
                // Normalize paths before comparison (MediaStore may return different path formats for same file).
                val normalizedInitialPath = initialFilePath?.let { normalizePath(it) }
                // Declared early so the scope check and the position-restore section share one instance.
                val normalizedCurrentPath = currentFilePath?.let { normalizePath(it) }
                // Scope check accepts the cache when the current (actively-playing) file is present -
                // avoids a full re-read just because the original launch file was sorted away.
                val cacheMatchesCurrentFile = normalizedCurrentPath != null &&
                    cachedFiles != null && cachedFiles.any { normalizePath(it.path) == normalizedCurrentPath }
                // Exclude stale initial path from scope check (already confirmed absent in prior reload).
                val effectiveInitialPath = if (initialFilePathIsStale) null else normalizedInitialPath
                val cacheMatchesInitialFile = cacheMatchesCurrentFile ||
                    effectiveInitialPath == null ||
                    (cachedFiles != null && cachedFiles.any { normalizePath(it.path) == effectiveInitialPath })

                val allFiles = if (cachedFiles != null && cachedFiles.isNotEmpty() && !cacheHasOnlyDirectories && cacheMatchesInitialFile) {
                    cachedFiles
                } else {
                    // Split cold-start (cache absent) from true scope mismatch (cache exists but wrong folder).
                    // Cold start is normal - downgrade to debug to avoid misleading "subfolder mismatch" noise.
                    if (cachedFiles == null) {
                        Timber.d("PlayerMediaFilesLoader: cache empty (cold start), loading from $initialFileDir")
                    } else if (!cacheMatchesInitialFile) {
                        Timber.w("PlayerMediaFilesLoader: cache scope mismatch - cached ${cachedFiles.size} files contain neither current ($currentFilePath) nor initial ($initialFilePath), reloading from $initialFileDir")
                    } else if (cacheHasOnlyDirectories) {
                        Timber.w("Cache contains only directories (${cachedFiles.size} items), loading actual files from current path")
                    } else {
                        Timber.w("Cache miss! Loading files via UseCase (slow path)")
                    }

                    val currentFolderPath = initialFileDir ?: stateFlow.value.currentFile?.path?.let { filePath ->
                        val lastSlash = filePath.lastIndexOf('/')
                        if (lastSlash > 0) filePath.substring(0, lastSlash) else null
                    }

                    Timber.d("PlayerViewModel: Loading files from currentFolderPath=$currentFolderPath")

                    getMediaFilesUseCase(
                        resource = resource,
                        sortMode = resource.sortMode,
                        sizeFilter = sizeFilter,
                        useChunkedLoading = false,
                        maxFiles = Int.MAX_VALUE,
                        onProgress = null,
                        currentPath = currentFolderPath,
                        isSubfolderMode = currentFolderPath != null
                    ).first()
                }

                Timber.d("PlayerViewModel: resource.supportedMediaTypes = ${resource.supportedMediaTypes}, allFiles=${resource.allFiles}")
                Timber.d("PlayerViewModel: allFiles count = ${allFiles.size}")

                // Match Browse behavior: resource-level allFiles overrides supportedMediaTypes filtering.
                // Player must not re-apply type filter when resource is configured to show all files.
                val files = if (resource.allFiles) {
                    val filtered = allFiles.filter { isPlayerBrowsableFile(it) }
                    Timber.d("PlayerViewModel: resource.allFiles=true, keeping all player-supported types: ${allFiles.size} → ${filtered.size}")
                    filtered
                } else if (resource.supportedMediaTypes.size < 7) {
                    val filtered = allFiles.filter { file ->
                        isPlayerBrowsableFile(file) && resource.supportedMediaTypes.contains(file.type)
                    }
                    Timber.d("Filtered files by supportedMediaTypes ${resource.supportedMediaTypes}: ${allFiles.size} → ${filtered.size}")
                    filtered
                } else {
                    val filtered = allFiles.filter { isPlayerBrowsableFile(it) }
                    Timber.d("All types supported (${resource.supportedMediaTypes.size}), filtered non-playable entries: ${allFiles.size} → ${filtered.size}")
                    filtered
                }

                val initialFileExistsInUnfiltered = normalizedInitialPath?.let { normalizedTarget ->
                    allFiles.any { file -> !file.isDirectory && normalizePath(file.path) == normalizedTarget }
                } ?: false

                // If the initial path is absent from the full source list, mark it stale so
                // subsequent reloadFiles() calls skip the scope check for this path.
                if (normalizedInitialPath != null && !initialFileExistsInUnfiltered) {
                    initialFilePathIsStale = true
                }

                val filesWithFavorites = try {
                    if (files.isEmpty()) {
                        files
                    } else {
                        val favoriteMap = favoritesUseCase.getFavoritesForPaths(
                            files.mapNotNull { file -> runCatching { file.path }.getOrNull() }
                        )
                        reconcileFavoriteFlags(
                            files = files,
                            favoriteMap = favoriteMap,
                            onFailure = { file, error ->
                                Timber.w(
                                    error,
                                    "Player media files: skipped favorite reconcile for %s",
                                    describeMediaFileForLog(file)
                                )
                            }
                        )
                    }
                } catch (e: Exception) {
                    e.warnUnlessCancellation("Player media files: failed to reconcile favorites, using existing flags")
                    files
                }

                if (filesWithFavorites.isEmpty()) {
                    sendEvent(PlayerViewModel.PlayerEvent.ShowError(context.getString(R.string.no_media_files_found)))
                    sendEvent(PlayerViewModel.PlayerEvent.FinishActivity)
                } else {
                    // Priority order for determining index:
                    // 1. Current file path (reloading during playback - preserve position)
                    // 2. Initial file path (from BrowseActivity - pagination mode)
                    // 3. Initial index (default fallback)
                    val safeIndex = if (normalizedCurrentPath != null) {
                        val foundIndex = filesWithFavorites.indexOfFirst { normalizePath(it.path) == normalizedCurrentPath }
                        if (foundIndex >= 0) {
                            Timber.d("Restored position to current file: $currentFilePath at index $foundIndex")
                            foundIndex
                        } else {
                            Timber.w("Current file not found: $currentFilePath, trying initialFilePath")
                            if (normalizedInitialPath != null) {
                                val initialFoundIndex = filesWithFavorites.indexOfFirst { normalizePath(it.path) == normalizedInitialPath }
                                // Neither current nor initial found - land at nearest-by-order position.
                                if (initialFoundIndex >= 0) initialFoundIndex else currentIndexBeforeReload.coerceIn(0, filesWithFavorites.size - 1)
                            } else {
                                initialIndex.coerceIn(0, filesWithFavorites.size - 1)
                            }
                        }
                    } else if (normalizedInitialPath != null) {
                        val foundIndex = filesWithFavorites.indexOfFirst { normalizePath(it.path) == normalizedInitialPath }
                        if (foundIndex >= 0) {
                            foundIndex
                        } else {
                            Timber.w("File not found by path: $initialFilePath, falling back to index $currentIndexBeforeReload")
                            if (resource.rememberFileList && !initialFileExistsInUnfiltered) {
                                val missingName = initialFilePath.substringAfterLast('/').ifBlank { initialFilePath }
                                // Auto-clean stale file reference (no blocking dialog)
                                MediaFilesCacheManager.removeFile(resource.id, initialFilePath)
                                cachedFileListRepository.deleteFile(resource.id, initialFilePath)
                                sendEvent(PlayerViewModel.PlayerEvent.ShowMissingFileInfo(missingName))
                            } else if (resource.rememberFileList && initialFileExistsInUnfiltered) {
                                Timber.w("Initial file exists in source list but was filtered out by supportedMediaTypes, skipping missing file dialog")
                            }
                            currentIndexBeforeReload.coerceIn(0, filesWithFavorites.size - 1)
                        }
                    } else {
                        initialIndex.coerceIn(0, files.size - 1)
                    }

                    // Always use resource-specific slideshow interval (takes precedence over global settings).
                    val intervalToUse = resource.slideshowInterval * 1000L

                    // CRITICAL: Recalculate showCommandPanel when resource is loaded, because loadSettings()
                    // may complete before the resource is available in state.
                    val currentSettings = settingsRepository.getSettings().first()
                    val showCommandPanel = when {
                        resource.showCommandPanel == null -> currentSettings.defaultShowCommandPanel
                        resource.showCommandPanel == false && currentSettings.defaultShowCommandPanel -> currentSettings.defaultShowCommandPanel
                        else -> resource.showCommandPanel
                    }
                    Timber.d("PlayerViewModel.loadMediaFiles: Determined showCommandPanel=$showCommandPanel (resource.showCommandPanel=${resource.showCommandPanel}, default=${currentSettings.defaultShowCommandPanel})")

                    // Auto-activate slideshow for audio/video library resources.
                    val autoSlideshow = resource.profile == ResourceProfile.AUDIO_LIBRARY ||
                        resource.profile == ResourceProfile.VIDEO_LIBRARY

                    // S0549: re-roll the random order at the start of a fresh playback-by-list session.
                    // Two triggers, both first-load only (files still empty) so reload/rotation keeps
                    // the current order and position:
                    //  - explicit random play (shuffleOnStart), and
                    //  - a RANDOM-sorted resource launched by list (no specific initialFilePath to land
                    //    on). Browse caches the RANDOM order with a seed it only re-rolls on explicit
                    //    reshuffle, so the player reused that cache and always opened on the same first
                    //    track. We shuffle the player's own copy here (the Browse cache is untouched, so
                    //    in-session scroll/re-entry stability is preserved) and land at the list head.
                    //  A tapped specific file (initialFilePath != null) is never re-rolled - the user
                    //  expects to start on that file in the existing order.
                    val isFreshSession = stateFlow.value.files.isEmpty()
                    val launchedByList = normalizedInitialPath == null
                    val rerollRandom = isFreshSession &&
                        (shuffleOnStart || (resource.sortMode == SortMode.RANDOM && launchedByList))
                    val finalFiles = if (rerollRandom) filesWithFavorites.shuffled() else filesWithFavorites
                    // After a re-roll the prior index no longer maps to a meaningful file; start at the head.
                    val finalIndex = if (rerollRandom) {
                        0
                    } else {
                        safeIndex
                    }

                    // Preserve isPaused on reload (user may have manually paused before background);
                    // only use resumeIsPlaying on first load (files list still empty).
                    val effectiveIsPaused = if (stateFlow.value.files.isNotEmpty()) {
                        stateFlow.value.isPaused
                    } else {
                        (resumeIsPlaying == false)
                    }
                    updateState {
                        it.copy(
                            files = finalFiles,
                            currentIndex = finalIndex,
                            resource = resource,
                            slideShowInterval = intervalToUse,
                            showCommandPanel = showCommandPanel,
                            isSlideShowActive = autoSlideshow,
                            isPaused = effectiveIsPaused
                        )
                    }
                }
                setLoading(false)
            } catch (e: Exception) {
                // A watchdog-aborted scan gets the scan-specific message; everything else keeps the
                // generic load-failed copy. The player cannot list files, so re-opening is the retry.
                e.rethrowIfCancellation()
                val msgRes = if (e is com.sza.fastmediasorter.data.network.exceptions.ScanTimeoutException) {
                    R.string.error_scan_timeout
                } else {
                    R.string.player_media_files_load_failed
                }
                sendEvent(
                    PlayerViewModel.PlayerEvent.ShowError(
                        context.getString(msgRes)
                    )
                )
                sendEvent(PlayerViewModel.PlayerEvent.FinishActivity)
                setLoading(false)
            }
        }
    }

    fun reloadFiles() = loadMediaFiles()

    fun cancelLoading() {
        loadingJob?.cancel()
        loadingJob = null
    }

    private fun isPlayerSupportedType(type: MediaType): Boolean = !type.isBinaryFile()

    private fun isPlayerBrowsableFile(file: MediaFile): Boolean =
        !file.isDirectory && isPlayerSupportedType(file.type)

    /**
     * Canonicalize a filesystem path so equality checks work across MediaStore format variants.
     * Protocol URIs (content://, smb://, sftp://, ftp://) are returned unchanged.
     */
    private fun normalizePath(path: String): String {
        if (path.startsWith("content://") || path.startsWith("smb://") ||
            path.startsWith("sftp://") || path.startsWith("ftp://")
        ) {
            return path
        }
        return try {
            java.io.File(path).canonicalPath
        } catch (e: Exception) {
            path
        }
    }
}
