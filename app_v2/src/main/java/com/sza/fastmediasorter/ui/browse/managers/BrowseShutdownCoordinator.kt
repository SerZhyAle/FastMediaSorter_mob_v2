package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.local.preferences.BrowseStateDataStore
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URI

/**
 * Encapsulates BrowseViewModel shutdown responsibilities:
 *   - Network-resource key derivation (used both at shutdown and for cancelling
 *     background thumbnail loading mid-session).
 *   - onCleared() side-effects: cancelling in-flight network ops, persisting the filter,
 *     and the best-effort trash + cache cleanup that must survive coroutine cancellation.
 *
 * The ViewModel keeps ownership of job references and of `super.onCleared()`; this coordinator
 * only performs the ancillary cleanup that has no reason to live in the ViewModel body.
 */
class BrowseShutdownCoordinator(
    private val stateFlow: StateFlow<BrowseState>,
    private val ioDispatcher: CoroutineDispatcher,
    private val browseStateDataStore: BrowseStateDataStore,
    private val unifiedCache: UnifiedFileCache,
    private val cleanupTrash: suspend (MediaResource) -> Unit
) {

    fun buildNetworkResourceKey(): String? {
        val r = stateFlow.value.resource ?: return null
        if (r.type == ResourceType.LOCAL) return null
        return when (r.type) {
            ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP -> {
                val uri = URI(r.path)
                "${uri.scheme}://${uri.host}:${uri.port.takeIf { it != -1 } ?: 445}"
            }
            ResourceType.CLOUD -> "cloud://${r.cloudProvider}/${r.cloudFolderId}"
            else -> null
        }
    }

    /** Cancels background thumbnail loading for the active network resource (if any). */
    fun cancelBackgroundThumbnailLoading() {
        buildNetworkResourceKey()?.let {
            ConnectionThrottleManager.cancelAllForResource(it)
            Timber.d("BrowseShutdownCoordinator: cancelled background thumbnail loading for $it")
        }
    }

    /**
     * Cancels connection-throttled ops and persists the current filter. Safe to call from
     * onCleared before `super.onCleared()` runs because it uses `viewModelScope.launch` via
     * the supplied scope (the ViewModel still owns the scope).
     */
    fun onShutdown(scope: CoroutineScope) {
        buildNetworkResourceKey()?.let {
            ConnectionThrottleManager.cancelAllForResource(it)
            Timber.d("BrowseShutdownCoordinator.onShutdown: cancelled ops for $it")
        }
        scope.launch(ioDispatcher) {
            browseStateDataStore.saveFilter(stateFlow.value.filter)
        }
    }

    /**
     * Best-effort post-shutdown cleanup: deletes expired trash and clears the unified cache.
     * Runs on a NonCancellable scope so it survives viewModelScope cancellation.
     */
    fun launchPostShutdownCleanup() {
        val resource = stateFlow.value.resource ?: return
        CoroutineScope(ioDispatcher + NonCancellable).launch {
            runCatching { cleanupTrash(resource) }
                .onFailure { Timber.w(it, "BrowseShutdownCoordinator: trash cleanup failed") }
            runCatching { unifiedCache.clearAll() }
                .onFailure { Timber.w(it, "BrowseShutdownCoordinator: cache cleanup failed") }
        }
    }
}
