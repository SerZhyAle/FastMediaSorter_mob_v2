package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.mutation.MutationJournal
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.SyncMediaStoreUseCase
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manages explicit refresh/reload flow in Browse screen.
 *
 * Responsibilities:
 * - Optional hard reset of visible list before re-scan.
 * - Local resource maintenance (MediaStore sync on reload; delegated trash cleanup elsewhere).
 * - Cache invalidation and forced resource reload.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
 */
class BrowseRefreshManager(
    private val syncMediaStoreUseCase: SyncMediaStoreUseCase,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val cachedFileListRepository: CachedFileListRepository,
    // S0242 Phase 03 - pull-to-refresh clears pending journal entries so the Reconciler
    // does not re-apply stale mutations to the freshly-fetched listing on next onResume.
    private val mutationJournal: MutationJournal,
    private val resourceId: Long,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val setLastEmittedMediaFilesNull: () -> Unit,
    private val setIgnoringFileChanges: (Boolean) -> Unit,
    private val loadResource: (Boolean) -> Unit
) {
    /**
     * Launches refresh pipeline and returns the running [Job] so caller can cancel/debounce.
     *
     * @param syncMediaStore When false, skips the MediaStore sync step. Pass false when this
     *   reload is triggered by a MediaStore ContentObserver - the OS already knows about the
     *   change, so running sync would produce more ContentObserver events and cause a loop.
     */
    fun launchReload(clearList: Boolean, syncMediaStore: Boolean = true): Job {
        // S0242 Phase 03: drop pending journal state for the current resource BEFORE the
        // rescan is enqueued. A full refresh fetches ground truth from the source, so any
        // pending entry would re-apply stale state to the fresh listing on next onResume.
        mutationJournal.clearResource(resourceId)
        Timber.d("BrowseRefreshManager: cleared MutationJournal for resource=$resourceId")
        return scope.launch(ioDispatcher) {
            val currentResource = stateFlow.value.resource

            if (clearList) {
                withContext(Dispatchers.Main) {
                    Timber.i("BrowseRefreshManager.reload: hard reset requested - clearing list")
                    updateState {
                        it.copy(
                            mediaFiles = emptyList(),
                            loadingProgress = 0,
                            totalFileCount = null,
                            isScanCancellable = false
                        )
                    }
                    setLastEmittedMediaFilesNull()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Timber.i("BrowseRefreshManager.reload: standard refresh - preserving list")
                    updateState {
                        it.copy(
                            loadingProgress = 0,
                            isScanCancellable = false
                        )
                    }
                }
            }

            if (currentResource?.type == ResourceType.LOCAL && syncMediaStore) {
                setIgnoringFileChanges(true)
                try {
                    Timber.i("BrowseRefreshManager.reload: syncing MediaStore for '${currentResource.name}'")
                    val syncResult = syncMediaStoreUseCase.invoke(currentResource)
                    syncResult.onSuccess { count ->
                        Timber.i("BrowseRefreshManager.reload: MediaStore sync completed - $count files")
                    }.onFailure { error ->
                        Timber.e(error, "BrowseRefreshManager.reload: MediaStore sync failed")
                    }
                } finally {
                    withContext(NonCancellable) {
                        kotlinx.coroutines.delay(500)
                        setIgnoringFileChanges(false)
                    }
                }
            } else if (currentResource?.type == ResourceType.LOCAL) {
                Timber.d("BrowseRefreshManager.reload: skipping MediaStore sync (observer-triggered)")
            }

            withContext(Dispatchers.Main) {
                MediaFilesCacheManager.clearCache(resourceId)
                if (currentResource?.rememberFileList == true) {
                    scope.launch(ioDispatcher) {
                        try {
                            cachedFileListRepository.deleteCachedFiles(resourceId)
                        } catch (e: Exception) {
                            Timber.e(e, "BrowseRefreshManager.reload: failed to clear DB cache for $resourceId")
                        }
                    }
                }
                // S1323: the two failure-cache resets used to live here on the assumption that a
                // reload is always a manual refresh. It is not - this path also runs on MediaStore
                // and FileObserver events, after every copy/move/delete, and on auth success, so a
                // thumbnail that timed out was re-tried (10 s per file over SFTP) on any file-system
                // activity in the folder. They now sit on the explicit refresh gesture instead, in
                // BrowseManagerInitializer.onRefreshClicked - which also removes the double reset
                // that fired once there and once here for a single pull-to-refresh.
                loadResource(true)
            }
        }
    }

    /**
     * Cleanup trash folders for [resource] on a background coroutine.
     * LOCAL: skipped so the periodic worker remains the only automatic finaliser.
     * Network (SMB/FTP/SFTP): delegates to [SmbOperationsUseCase.cleanupTrash].
     */
    fun cleanupTrashOnBackground(resource: MediaResource) {
        scope.launch(ioDispatcher) {
            try {
                when (resource.type) {
                    ResourceType.LOCAL -> {
                        // S0209: local reload/shutdown must not collapse the restore TTL window.
                        Timber.d("BrowseRefreshManager: LOCAL trash cleanup skipped - periodic worker handles TTL cleanup")
                    }
                    else -> {
                        val credentialsId = resource.credentialsId
                        if (credentialsId.isNullOrBlank()) {
                            Timber.w("BrowseRefreshManager: network trash cleanup skipped - missing credentialsId for '${resource.name}'")
                            return@launch
                        }
                        val result = smbOperationsUseCase.cleanupTrash(
                            type = resource.type,
                            credentialsId = credentialsId,
                            path = resource.path
                        )
                        if (result.isSuccess) {
                            val count = result.getOrDefault(0)
                            if (count > 0) Timber.i("BrowseRefreshManager: cleaned $count network trash folders in '${resource.name}'")
                            else Timber.d("BrowseRefreshManager: no network trash folders for '${resource.name}'")
                        } else {
                            Timber.w(result.exceptionOrNull(), "BrowseRefreshManager: network trash cleanup failed for '${resource.name}'")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "BrowseRefreshManager: trash cleanup failed for '${resource.name}'")
            }
        }
    }
}