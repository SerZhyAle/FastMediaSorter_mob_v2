package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.cache.VideoPlaybackFailureSessionCache
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.CleanupTrashFoldersUseCase
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
 * - Local resource pre-refresh maintenance (trash cleanup + MediaStore sync).
 * - Cache invalidation and forced resource reload.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition — IV.1).
 */
class BrowseRefreshManager(
    private val cleanupTrashFoldersUseCase: CleanupTrashFoldersUseCase,
    private val syncMediaStoreUseCase: SyncMediaStoreUseCase,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val cachedFileListRepository: CachedFileListRepository,
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
     *   reload is triggered by a MediaStore ContentObserver — the OS already knows about the
     *   change, so running sync would produce more ContentObserver events and cause a loop.
     */
    fun launchReload(clearList: Boolean, syncMediaStore: Boolean = true): Job {
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

            if (currentResource?.type == ResourceType.LOCAL) {
                try {
                    val rootDir = File(currentResource.path)
                    Timber.i("BrowseRefreshManager.reload: cleaning .trash folders in '${currentResource.name}'")
                    val deletedCount = cleanupTrashFoldersUseCase.cleanup(rootDir, maxAgeMs = 0L)
                    Timber.i("BrowseRefreshManager.reload: deleted $deletedCount .trash folders")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "BrowseRefreshManager.reload: failed to cleanup .trash folders")
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
                // Manual refresh means "re-check this file again", so drop session-only timeout hints too.
                VideoPlaybackFailureSessionCache.clearAll()
                NetworkFileDataFetcher.clearFailedVideoCache()
                loadResource(true)
            }
        }
    }

    /**
     * Cleanup trash folders for [resource] on a background coroutine.
     * LOCAL: removes `.trash_*` subdirectories older than [maxAgeMs].
     * Network (SMB/FTP/SFTP): delegates to [SmbOperationsUseCase.cleanupTrash].
     */
    fun cleanupTrashOnBackground(resource: MediaResource, maxAgeMs: Long) {
        scope.launch(ioDispatcher) {
            try {
                when (resource.type) {
                    ResourceType.LOCAL -> {
                        val rootDir = java.io.File(resource.path)
                        if (!rootDir.exists() || !rootDir.isDirectory) return@launch
                        val deletedCount = cleanupTrashFoldersUseCase.cleanup(rootDir, maxAgeMs)
                        if (deletedCount > 0) {
                            Timber.i("BrowseRefreshManager: cleaned $deletedCount trash folders in '${resource.name}'")
                        }
                    }
                    else -> {
                        val credentialsId = resource.credentialsId
                        if (credentialsId.isNullOrBlank()) {
                            Timber.w("BrowseRefreshManager: network trash cleanup skipped — missing credentialsId for '${resource.name}'")
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