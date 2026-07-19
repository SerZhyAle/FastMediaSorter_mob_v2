package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S0404 (ADR-10): the files behind a resource-backed desktop gadget - the playlist and the folder
 * preview. One place, because both gadgets must obey the same rule and a rule implemented twice is a
 * rule that will drift.
 *
 * The rule exists because neither obvious option works on a home screen:
 * - Cache-only (the `RandomPhotoFrameWidgetRefresher` pattern) shows "Unavailable" after **every
 *   reboot**: [MediaFilesCacheManager] is process memory, and `rememberFileList` defaults to false, so
 *   the persisted cache is usually empty too.
 * - Always-scan hits SMB/FTP/SFTP/Cloud on every Home press, breaking the surface's own invariant that
 *   Home stays cheap (strategic §3.2).
 *
 * So: **every** read is cache-first, and only a LOCAL resource may fall back to a scan when both caches
 * are cold. A network resource never scans from Home. Phase 07's add-flow enables `rememberFileList` on
 * a gadget's resource, which is what fills the persisted cache and makes both paths survive a reboot.
 *
 * The cache-first step is this class's own job and must not be delegated to [GetMediaFilesUseCase]: its
 * skip-scan gate requires `resource.rememberFileList`, which defaults to false, so an un-flagged
 * resource would be physically rescanned on every single return to Home.
 *
 * Ordering is also this class's job. [GetMediaFilesUseCase] **skips sorting entirely** above its
 * large-folder threshold, and a cached list carries whatever order it was stored with - so taking the
 * first N of either would silently mean "N arbitrary files" on exactly the big libraries where a
 * preview matters most.
 */
class LoadLauncherGadgetFilesUseCase @Inject constructor(
    private val getResources: GetResourcesUseCase,
    private val getMediaFiles: GetMediaFilesUseCase,
    private val cachedFileListRepository: CachedFileListRepository,
) {

    sealed interface Result {
        /** The resource is gone, or a network resource has nothing cached yet. */
        data object Unavailable : Result
        data class Files(val resourceName: String, val files: List<MediaFile>) : Result
    }

    suspend operator fun invoke(
        resourceId: Long,
        limit: Int,
        sortMode: SortMode = SortMode.DATE_DESC,
    ): Result = withContext(Dispatchers.IO) {
        val resource = getResources.getById(resourceId) ?: return@withContext Result.Unavailable
        val files = cachedFiles(resourceId)
            ?: if (resource.type.isNetworkResource) null else localFiles(resource)
        val usable = files.orEmpty().filterNot { it.isDirectory }
        if (usable.isEmpty()) {
            Result.Unavailable
        } else {
            Result.Files(resource.name, order(usable, sortMode).take(limit))
        }
    }

    private suspend fun cachedFiles(resourceId: Long): List<MediaFile>? =
        MediaFilesCacheManager.getCachedList(resourceId)
            ?: runCatching { cachedFileListRepository.getCachedFiles(resourceId) }
                .rethrowCancellation()
                .onFailure { Timber.w(it, "Launcher gadget: cached file list unreadable for %d", resourceId) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }

    /**
     * `first()` takes the single complete emission - the progressive/chunked early-emit paths need
     * flags this call never passes, so there is no partial list to guard against.
     */
    private suspend fun localFiles(resource: MediaResource): List<MediaFile>? = runCatching {
        getMediaFiles(resource = resource).first()
    }.rethrowCancellation()
        .onFailure { Timber.w(it, "Launcher gadget: local scan failed for %s", resource.name) }
        .getOrNull()

    /**
     * Ordered here rather than trusted from the source: a cached list carries its stored order, and a
     * fresh scan of a large folder comes back unsorted by design. Sorting a few thousand already-loaded
     * items to pick 4 costs nothing next to the scan that produced them.
     */
    private fun order(files: List<MediaFile>, sortMode: SortMode): List<MediaFile> = when (sortMode) {
        SortMode.DATE_DESC -> files.sortedByDescending { it.createdDate }
        SortMode.DATE_ASC -> files.sortedBy { it.createdDate }
        SortMode.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
        // Everything a gadget asks for today is one of the above; NAME_ASC is the sane default for the
        // rest rather than silently honouring an order nobody chose.
        else -> files.sortedBy { it.name.lowercase() }
    }

    /**
     * `runCatching` catches [Throwable], so a plain `onFailure` would report the user simply leaving
     * Home as a scan failure. Same convention as [GetMediaFilesUseCase] (S0742).
     */
    private fun <T> kotlin.Result<T>.rethrowCancellation(): kotlin.Result<T> = also {
        (exceptionOrNull() as? CancellationException)?.let { throw it }
    }
}
