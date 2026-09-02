package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2370: reconnects a direct-path local resource onto a system folder tree picked by the user,
 * preserving the resource's identity - the address is rewritten in place, so schedules (cascade-
 * deleted with their resource row) and quick-sort destinations survive by construction.
 *
 * Ordering: the address is rewritten before the favorites remap so the old-path prefix is captured
 * exactly once, and the file-list cache dies last so no concurrent scan repopulates it from the old
 * address mid-swap.
 */
class ReconnectResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val favoritesRepository: FavoritesRepository,
    private val cachedFileListRepository: CachedFileListRepository,
) {

    data class Outcome(val remappedFavorites: Int, val keptFavorites: Int)

    suspend operator fun invoke(resourceId: Long, treeUriString: String): Result<Outcome> {
        val resource = resourceRepository.getResourceById(resourceId)
        val refusal = refusalFor(resourceId, resource)
        if (refusal != null) {
            return Result.failure(refusal)
        }
        val oldPath = requireNotNull(resource).path
        return runSwap(resourceId, oldPath, treeUriString)
    }

    /**
     * Why the swap must not start, or null when it may. Separated from the swap so the guards cost no
     * write attempt and so the reasons stay readable as one list rather than three scattered throws.
     */
    private fun refusalFor(resourceId: Long, resource: com.sza.fastmediasorter.domain.model.MediaResource?) = when {
        resource == null -> IllegalArgumentException("Reconnect: resource $resourceId not found")
        resource.type != ResourceType.LOCAL ->
            IllegalArgumentException("Reconnect: resource $resourceId is ${resource.type}, not LOCAL")
        resource.path.startsWith(TREE_ADDRESS_PREFIX) ->
            IllegalArgumentException("Reconnect: resource $resourceId already uses a tree address")
        // A virtual aggregate points at no folder of its own, so an address swap would give it one that
        // is not its to own. The menu already hides the action for these; this is the enforcement half.
        resource.path in VirtualPathUtils.ALL_VIRTUAL_PATHS ->
            IllegalArgumentException("Reconnect: resource $resourceId is a virtual aggregate")
        else -> null
    }

    /**
     * The three writes, with the address restored when a later one fails.
     *
     * There is no shared transaction to lean on here - the writes cross three repositories - so the
     * address is put back by hand. Without it a failed remap leaves a resource pointing at the tree
     * with its favorites still on raw paths, and the guard above then refuses every retry because the
     * address it reads is already a tree: the user would be locked out of their own resource.
     *
     * [NonCancellable] covers the same window: cancellation between the first and last write would
     * otherwise strand exactly that half-applied state.
     */
    // The broad arm is the point, not an oversight: a compensating handler must undo the first write
    // for EVERY later failure - a constraint violation, a revoked grant, a provider that went away -
    // and a narrower catch would silently pick which of those strand the resource. Cancellation is
    // re-thrown by its own arm above it, so this one never swallows it.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runSwap(resourceId: Long, oldPath: String, treeUriString: String): Result<Outcome> =
        withContext(NonCancellable) {
            resourceRepository.updateResourceAddress(resourceId, treeUriString)
            try {
                val remap = favoritesRepository.remapResourceFavoritesToTree(resourceId, oldPath, treeUriString)
                cachedFileListRepository.deleteCachedFiles(resourceId)
                Result.success(Outcome(remappedFavorites = remap.remapped, keptFavorites = remap.keptMissing))
            } catch (e: CancellationException) {
                restoreAddress(resourceId, oldPath)
                throw e
            } catch (e: Exception) {
                restoreAddress(resourceId, oldPath)
                Result.failure(e)
            }
        }

    // Same reason as [runSwap]: this is the last line of defence, so it may not choose which failures
    // it reports. CancellationException gets its own arm first - it is an IllegalStateException
    // subtype, so the broad arm below would otherwise swallow it (S1363/S1889).
    @Suppress("TooGenericExceptionCaught")
    private suspend fun restoreAddress(resourceId: Long, oldPath: String) {
        try {
            resourceRepository.updateResourceAddress(resourceId, oldPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing further can be done from here, but a resource stranded on a tree address it has
            // no favorites for is worth naming in the log rather than losing silently.
            Timber.e(e, "Reconnect: could not restore address of resource %d", resourceId)
        }
    }

    private companion object {
        const val TREE_ADDRESS_PREFIX = "content://"
    }
}
