package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Browse-side state synchroniser. Two responsibilities only:
 * - Load the virtual Favorites resource into [BrowseState].
 * - Detect resource setting changes from the database on `onResume` and trigger a reload
 *   when the user has edited supportedMediaTypes / scanSubdirectories / showSubfoldersAsItems in
 *   `ResourceEditorActivity` while Browse was paused.
 *
 * S0242 Phase 03 - the structural-equality cache-comparison fast-path was removed.
 * Browse list synchronisation against player-side mutations is owned exclusively by
 * `BrowseReconcilerManager`, which `BrowseActivity.onResumeWithViews()` runs before
 * calling [checkAndReloadIfResourceChanged]. Cache and visible list stay coherent through
 * the journal; the resource-settings check below only watches for resource-level option
 * changes the journal cannot represent.
 */
class BrowseStateSyncManager(
    private val useCases: BrowseStateSyncUseCases,
    private val resourceId: Long,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val scheduleWarmupIfEligible: suspend (List<MediaFile>) -> Unit,
    private val reloadFiles: (Boolean) -> Unit
) {
    fun loadFavorites() {
        Timber.d("BrowseStateSyncManager.loadFavorites: START")

        scope.launch(ioDispatcher) {
            setLoading(true)
            useCases.favoritesUseCase.getAllFavorites().collect { favorites ->
                Timber.d("BrowseStateSyncManager.loadFavorites: Received ${favorites.size} favorites")

                // S0783: STREAM rows get their display name from the live catalog (MaterializeFavoritesUseCase).
                val mediaFiles = useCases.materializeFavoritesUseCase.toMediaFiles(favorites)

                MediaFilesCacheManager.setCachedList(FAVORITES_RESOURCE_ID, mediaFiles)

                updateState {
                    it.copy(
                        mediaFiles = mediaFiles,
                        totalFileCount = mediaFiles.size,
                        loadingProgress = mediaFiles.size
                    )
                }
                scheduleWarmupIfEligible(mediaFiles)
                setLoading(false)
                Timber.d("BrowseStateSyncManager.loadFavorites: COMPLETE")
            }
        }
    }

    fun checkAndReloadIfResourceChanged() {
        Timber.d("BrowseStateSyncManager.checkAndReloadIfResourceChanged: START - resourceId=$resourceId")
        val currentResource = stateFlow.value.resource ?: return

        if (currentResource.id == FAVORITES_RESOURCE_ID) {
            // Favorites are journal-irrelevant (synthesized from the favorites table on
            // every load). The Reconciler is a no-op for the virtual id; nothing to do here.
            Timber.d("BrowseStateSyncManager.checkAndReloadIfResourceChanged: favorites resource, no settings to reload")
            return
        }

        scope.launch(ioDispatcher) {
            val updatedResource = useCases.getResourcesUseCase.getById(resourceId)
            if (updatedResource == null) {
                Timber.w("BrowseStateSyncManager.checkAndReloadIfResourceChanged: resource not found")
                return@launch
            }

            val typesChanged = currentResource.supportedMediaTypes != updatedResource.supportedMediaTypes
            val subfoldersChanged = currentResource.scanSubdirectories != updatedResource.scanSubdirectories
            // S1315: a resource edited from Main is opened with a plain startActivity, so no result
            // ever reaches Browse and this resume-time comparison is the only thing that notices the
            // change. It has to cover every setting that reshapes the list, not just the first two.
            val showAsItemsChanged =
                currentResource.showSubfoldersAsItems != updatedResource.showSubfoldersAsItems
            Timber.d("S1315: resume diff types=$typesChanged scan=$subfoldersChanged show=$showAsItemsChanged")

            if (typesChanged || subfoldersChanged || showAsItemsChanged) {
                Timber.d("BrowseStateSyncManager.checkAndReloadIfResourceChanged: settings changed, reloading")
                Timber.d("  supportedMediaTypes: ${currentResource.supportedMediaTypes} -> ${updatedResource.supportedMediaTypes}")
                Timber.d("  scanSubdirectories: ${currentResource.scanSubdirectories} -> ${updatedResource.scanSubdirectories}")

                MediaFilesCacheManager.clearCache(resourceId)
                updateState { it.copy(resource = updatedResource) }
                reloadFiles(true)
            } else {
                // S0242 Phase 03 - Reconciler already ran before this check fires; no
                // structural-equality fall-back needed.
                Timber.d("BrowseStateSyncManager.checkAndReloadIfResourceChanged: no settings change; Reconciler owns cache sync")
            }
        }
    }

    private companion object {
        const val FAVORITES_RESOURCE_ID = -100L
    }
}
