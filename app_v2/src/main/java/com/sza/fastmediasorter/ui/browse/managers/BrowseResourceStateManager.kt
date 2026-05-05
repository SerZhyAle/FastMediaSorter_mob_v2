package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.usecase.AddResourceAsDestinationUseCase
import com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages resource and file state persistence in the Browse screen:
 * - Optimistic favorite toggle with DB rollback on failure ([toggleFavorite]).
 * - Persist last viewed file path ([saveLastViewedFile]).
 * - Persist scroll position ([saveScrollPosition]).
 * - Clear resume state on explicit exit ([clearResumeState]).
 * - Refresh resource metadata (name/sort/display) without file reload ([refreshResourceMetadata]).
 * - Register current resource as a Quick Sort destination ([addCurrentResourceAsDestination]).
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition — IV.1).
 */
class BrowseResourceStateManager(
    private val favoritesUseCase: FavoritesUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val addResourceAsDestinationUseCase: AddResourceAsDestinationUseCase,
    private val clearResumeStateUseCase: ClearResumeStateUseCase,
    private val windowIdProvider: () -> String,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val exceptionHandler: CoroutineContext,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val resourceId: Long
) {
    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Optimistically toggle [file]'s favorite flag in the list, then persist to DB.
     * Rolls back the UI change if the DB write fails.
     */
    fun toggleFavorite(file: MediaFile) {
        val resource = stateFlow.value.resource ?: return

        Timber.d(
            "BrowseResourceStateManager.toggleFavorite: '${file.name}', current=${file.isFavorite}, resourceId=${resource.id}"
        )

        val newFavoriteState = !file.isFavorite
        updateState { current ->
            current.copy(
                mediaFiles = current.mediaFiles.map {
                    if (it.path == file.path) it.copy(isFavorite = newFavoriteState) else it
                }
            )
        }

        scope.launch(ioDispatcher + exceptionHandler) {
            try {
                favoritesUseCase.toggleFavorite(file, resource.id)
            } catch (e: Exception) {
                Timber.e(e, "BrowseResourceStateManager.toggleFavorite: failed for ${file.path}")
                updateState { current ->
                    current.copy(
                        mediaFiles = current.mediaFiles.map {
                            if (it.path == file.path) it.copy(isFavorite = !newFavoriteState) else it
                        }
                    )
                }
                sendEvent(BrowseEvent.ShowError("Failed to update favorite status"))
            }
        }
    }

    /** Persist [filePath] as the last viewed file for the current resource. */
    fun saveLastViewedFile(filePath: String) {
        val resource = stateFlow.value.resource ?: return
        val updatedResource = resource.copy(lastViewedFile = filePath)
        updateState { it.copy(resource = updatedResource) }
        scope.launch(ioDispatcher + exceptionHandler) {
            updateResourceUseCase(updatedResource)
            Timber.d("BrowseResourceStateManager: saved lastViewedFile=$filePath for '${resource.name}'")
        }
    }

    /** Persist [position] as the last scroll position for the current resource. */
    fun saveScrollPosition(position: Int) {
        val resource = stateFlow.value.resource ?: return
        val updatedResource = resource.copy(lastScrollPosition = position)
        updateState { it.copy(resource = updatedResource) }
        scope.launch(ioDispatcher + exceptionHandler) {
            withContext(NonCancellable) {
                updateResourceUseCase(updatedResource)
                Timber.d("BrowseResourceStateManager: saved lastScrollPosition=$position for '${resource.name}'")
            }
        }
    }

    /** Clear the resume state when the user explicitly exits the browser or switches resource. */
    fun clearResumeState() {
        scope.launch {
            Timber.d("BrowseResourceStateManager: clearResumeState")
            clearResumeStateUseCase(windowIdProvider())
        }
    }

    /**
     * Reload resource record from DB and update state (name, sortMode, displayMode).
     * Does NOT reload the file list — only updates the resource metadata.
     */
    fun refreshResourceMetadata() {
        scope.launch(ioDispatcher) {
            Timber.i("BrowseResourceStateManager.refreshResourceMetadata: loading resource $resourceId from DB")
            val updatedResource = getResourcesUseCase.getById(resourceId)
            if (updatedResource != null) {
                val effectiveResource = if (updatedResource.isAudioOnly() &&
                    updatedResource.displayMode != DisplayMode.LIST
                ) {
                    updatedResource.copy(displayMode = DisplayMode.LIST)
                } else {
                    updatedResource
                }
                withContext(Dispatchers.Main) {
                    Timber.i("BrowseResourceStateManager.refreshResourceMetadata: '${effectiveResource.name}', sortMode=${effectiveResource.sortMode}")
                    updateState {
                        it.copy(
                            resource = effectiveResource,
                            sortMode = effectiveResource.sortMode,
                            displayMode = effectiveResource.displayMode
                        )
                    }
                }
                if (effectiveResource != updatedResource) {
                    updateResourceUseCase(effectiveResource)
                }
            } else {
                Timber.e("BrowseResourceStateManager.refreshResourceMetadata: resource $resourceId not found")
            }
        }
    }

    /**
     * Register the current resource as a Quick Sort destination.
     * Fires [BrowseEvent.ResourceAddedAsDestination] on success or [BrowseEvent.ShowError] on failure.
     */
    fun addCurrentResourceAsDestination() {
        val resource = stateFlow.value.resource ?: return
        scope.launch {
            val result = withContext(ioDispatcher) { addResourceAsDestinationUseCase(resource) }
            result.onSuccess {
                sendEvent(BrowseEvent.ResourceAddedAsDestination(resource.id))
            }.onFailure { e ->
                sendEvent(BrowseEvent.ShowError(e.message ?: "Failed to add as destination"))
            }
        }
    }
}
