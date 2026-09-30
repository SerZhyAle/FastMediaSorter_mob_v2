package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.network.extractNetworkResourceKey
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
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
    private val hasActiveTransfer: suspend () -> Boolean,
    private val cleanupTrash: suspend (MediaResource) -> Unit,
    private val resourceId: Long
) {

    fun buildNetworkResourceKey(): String? {
        val r = stateFlow.value.resource ?: return null
        if (r.type == ResourceType.LOCAL) return null
        return when (r.type) {
            // S3069: string parsing, not java.net.URI - a share named with a space is a legal
            // SMB path and an illegal URI, and this runs on the onCleared path where a throw crashes.
            ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP -> extractNetworkResourceKey(r.path)
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
     * Cancels connection-throttled ops and persists the current filter. Called from onCleared
     * just before `super.onCleared()` cancels the supplied ViewModel scope, so the filter save
     * must carry NonCancellable (S3779) to complete - the same pattern the throttle-cleanup
     * arm and [launchPostShutdownCleanup] already use.
     */
    fun onShutdown(scope: CoroutineScope) {
        val resourceKey = buildNetworkResourceKey()
        CoroutineScope(ioDispatcher + NonCancellable).launch {
            if (hasActiveTransfer()) {
                Timber.i("BrowseShutdownCoordinator: skipped throttle cleanup during active transfer")
            } else {
                resourceKey?.let {
                    ConnectionThrottleManager.cancelAllForResource(it)
                    Timber.d("BrowseShutdownCoordinator.onShutdown: cancelled ops for $it")
                }
            }
        }
        scope.launch(ioDispatcher + NonCancellable) {
            browseStateDataStore.saveFilter(resourceId, stateFlow.value.filter)
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
                .onFailure { it.warnUnlessCancellation("BrowseShutdownCoordinator: trash cleanup failed") }
            if (hasActiveTransfer()) {
                Timber.i("BrowseShutdownCoordinator: skipped cache cleanup during active transfer")
            } else {
                runCatching { unifiedCache.clearAll() }
                    .onFailure { it.warnUnlessCancellation("BrowseShutdownCoordinator: cache cleanup failed") }
            }
        }
    }
}
