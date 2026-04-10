package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages favorites loading and cache-to-state synchronization for the Browse screen.
 *
 * Responsibilities:
 * - Load the virtual Favorites resource into BrowseState.
 * - Sync in-memory UI state from MediaFilesCacheManager when returning from PlayerActivity.
 * - Detect resource setting changes from the database and trigger a reload when needed.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition — IV.1).
 */
class BrowseStateSyncManager(
    private val favoritesUseCase: FavoritesUseCase,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val cachedFileListRepository: CachedFileListRepository,
    private val resourceId: Long,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val scheduleWarmupIfEligible: suspend (List<MediaFile>) -> Unit,
    private val applyFilterToList: (List<MediaFile>, com.sza.fastmediasorter.domain.model.FileFilter) -> List<MediaFile>,
    private val reloadCurrentSubfolder: (String) -> Unit,
    private val reloadFiles: (Boolean) -> Unit
) {
    fun loadFavorites() {
        Timber.d("BrowseStateSyncManager.loadFavorites: START")

        scope.launch(ioDispatcher) {
            setLoading(true)
            favoritesUseCase.getAllFavorites().collect { favorites ->
                Timber.d("BrowseStateSyncManager.loadFavorites: Received ${favorites.size} favorites")

                val mediaFiles = favorites.map { entity ->
                    MediaFile(
                        path = entity.uri,
                        name = entity.displayName,
                        type = MediaType.entries.getOrElse(entity.mediaType) { MediaType.IMAGE },
                        size = entity.size,
                        createdDate = entity.dateModified,
                        isFavorite = true,
                        resourceId = entity.resourceId,
                        width = 0,
                        height = 0,
                        duration = 0
                    )
                }

                MediaFilesCacheManager.setCachedList(FAVORITES_RESOURCE_ID, mediaFiles)

                updateState {
                    it.copy(
                        mediaFiles = mediaFiles,
                        totalFileCount = mediaFiles.size,
                        loadingProgress = mediaFiles.size,
                        usePagination = false
                    )
                }
                scheduleWarmupIfEligible(mediaFiles)
                setLoading(false)
                Timber.d("BrowseStateSyncManager.loadFavorites: COMPLETE")
            }
        }
    }

    fun syncWithCache() {
        val currentSubPath = stateFlow.value.currentPath
        if (stateFlow.value.isSubfolderMode && currentSubPath != null) {
            Timber.d("BrowseStateSyncManager.syncWithCache: subfolder mode at '$currentSubPath', reloading subfolder")
            reloadCurrentSubfolder(currentSubPath)
            return
        }

        val cachedFiles = MediaFilesCacheManager.getCachedList(resourceId) ?: return
        val currentFiles = stateFlow.value.mediaFiles
        val resource = stateFlow.value.resource

        var filteredFiles = if (resource != null && !resource.allFiles && resource.supportedMediaTypes.isNotEmpty()) {
            cachedFiles.filter { file ->
                file.isDirectory || resource.supportedMediaTypes.contains(file.type)
            }
        } else {
            cachedFiles
        }

        val filter = stateFlow.value.filter
        filteredFiles = if (filter != null) applyFilterToList(filteredFiles, filter) else filteredFiles

        Timber.d("BrowseStateSyncManager.syncWithCache: ${cachedFiles.size} cached -> ${filteredFiles.size} visible")

        if (filteredFiles != currentFiles) {
            updateState { it.copy(mediaFiles = filteredFiles, totalFileCount = filteredFiles.size) }
            Timber.d("BrowseStateSyncManager.syncWithCache: state updated")

            if (resource?.rememberFileList == true) {
                scope.launch(ioDispatcher) {
                    try {
                        cachedFileListRepository.saveCachedFiles(resource.id, filteredFiles)
                    } catch (e: Exception) {
                        Timber.e(e, "BrowseStateSyncManager.syncWithCache: failed to persist DB cache for resource ${resource.id}")
                    }
                }
            }
        } else {
            Timber.d("BrowseStateSyncManager.syncWithCache: state already up to date")
        }
    }

    fun checkAndReloadIfResourceChanged() {
        Timber.d("BrowseStateSyncManager.checkAndReloadIfResourceChanged: START - resourceId=$resourceId")
        val currentResource = stateFlow.value.resource ?: return

        if (currentResource.id == FAVORITES_RESOURCE_ID) {
            syncWithCache()
            return
        }

        scope.launch(ioDispatcher) {
            val updatedResource = getResourcesUseCase.getById(resourceId)
            if (updatedResource == null) {
                Timber.w("BrowseStateSyncManager.checkAndReloadIfResourceChanged: resource not found")
                return@launch
            }

            val typesChanged = currentResource.supportedMediaTypes != updatedResource.supportedMediaTypes
            val subfoldersChanged = currentResource.scanSubdirectories != updatedResource.scanSubdirectories

            if (typesChanged || subfoldersChanged) {
                Timber.d("BrowseStateSyncManager.checkAndReloadIfResourceChanged: settings changed, reloading")
                Timber.d("  supportedMediaTypes: ${currentResource.supportedMediaTypes} -> ${updatedResource.supportedMediaTypes}")
                Timber.d("  scanSubdirectories: ${currentResource.scanSubdirectories} -> ${updatedResource.scanSubdirectories}")

                MediaFilesCacheManager.clearCache(resourceId)
                updateState { it.copy(resource = updatedResource) }
                reloadFiles(true)
            } else {
                Timber.d("BrowseStateSyncManager.checkAndReloadIfResourceChanged: no changes, syncing cache")
                syncWithCache()
            }
        }
    }

    private companion object {
        const val FAVORITES_RESOURCE_ID = -100L
    }
}