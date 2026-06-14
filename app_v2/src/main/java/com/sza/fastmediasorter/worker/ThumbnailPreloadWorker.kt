package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import com.sza.fastmediasorter.domain.usecase.CalculateOptimalCacheSizeUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Background worker that proactively fills the thumbnail cache for a resource's file list.
 * Triggered after each successful NetworkFilesSyncWorker run when enableThumbnailPreload is ON.
 *
 * Input data:
 *   - KEY_RESOURCE_ID (Long): resource to preload
 *   - KEY_MAX_ITEMS (Int, optional): cap on files processed per run (default 200)
 *
 * Part of X.11: Background Thumbnail Preload.
 */
@HiltWorker
class ThumbnailPreloadWorker @AssistedInject constructor(
    @Assisted applicationContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val cachedFileListRepository: CachedFileListRepository,
    private val resourceRepository: ResourceRepository,
    private val thumbnailCacheRepository: ThumbnailCacheRepository,
    private val calculateOptimalCacheSizeUseCase: CalculateOptimalCacheSizeUseCase,
    private val thumbnailExtractorHelper: ThumbnailExtractorHelper,
    private val remoteSourceGate: RemoteSourceAvailabilityGate
) : CoroutineWorker(applicationContext, workerParams) {

    companion object {
        const val KEY_RESOURCE_ID = "resource_id"
        const val KEY_MAX_ITEMS = "max_items"
        const val DEFAULT_MAX_ITEMS = 200

        fun workName(resourceId: Long) = "thumbnail_preload_$resourceId"
    }

    override suspend fun doWork(): Result {
        val resourceId = inputData.getLong(KEY_RESOURCE_ID, -1L)
        if (resourceId < 0) {
            Timber.w("ThumbnailPreloadWorker: invalid resourceId=$resourceId - skipping")
            return Result.success()
        }
        val maxItems = inputData.getInt(KEY_MAX_ITEMS, DEFAULT_MAX_ITEMS)

        // Step 1: Abort if feature disabled
        val settings = settingsRepository.getSettings().first()
        if (!settings.enableThumbnailPreload) {
            Timber.d("ThumbnailPreloadWorker: feature disabled - skipping resource $resourceId")
            return Result.success()
        }

        // Step 2: Verify resource still exists
        val resource = resourceRepository.getResourceById(resourceId)
        if (resource == null) {
            Timber.d("ThumbnailPreloadWorker: resource $resourceId deleted - skipping")
            return Result.success()
        }

        // S0391: defense-in-depth - never preload thumbnails for a disabled source's resource.
        if (!remoteSourceGate.isEnabled(resource)) {
            Timber.d("ThumbnailPreloadWorker: resource ${resource.name} source disabled - skipping")
            return Result.success()
        }

        // Step 3: Load cached file list
        val allFiles = cachedFileListRepository.getCachedFiles(resourceId)
        if (allFiles.isNullOrEmpty()) {
            Timber.d("ThumbnailPreloadWorker: no cached file list for resource $resourceId - skipping")
            return Result.success()
        }

        // Step 4: Filter to video + PDF, respecting settings; cap at maxItems
        val candidates = allFiles.filter { file ->
            when (file.type) {
                MediaType.VIDEO -> settings.showVideoThumbnails
                MediaType.PDF -> settings.showPdfThumbnails
                else -> false
            }
        }.take(maxItems)

        if (candidates.isEmpty()) {
            Timber.d("ThumbnailPreloadWorker: no candidates for resource $resourceId")
            return Result.success()
        }

        Timber.i("ThumbnailPreloadWorker: preloading ${candidates.size} files for resource ${resource.name}")

        // Step 5: Determine protocol for throttling
        val protocol = when (resource.type) {
            ResourceType.SMB -> ConnectionThrottleManager.ProtocolLimits.SMB
            ResourceType.SFTP -> ConnectionThrottleManager.ProtocolLimits.SFTP
            ResourceType.FTP -> ConnectionThrottleManager.ProtocolLimits.FTP
            else -> ConnectionThrottleManager.ProtocolLimits.LOCAL
        }

        var processedCount = 0
        for (file in candidates) {
            if (isStopped) {
                Timber.i("ThumbnailPreloadWorker: cancelled after $processedCount files")
                break
            }

            // Skip already-cached
            if (thumbnailCacheRepository.getCachedThumbnail(file.path) != null) {
                continue
            }

            try {
                val jpegFile = ConnectionThrottleManager.withThrottle(protocol, resource.path) {
                    thumbnailExtractorHelper.extract(file)
                }
                if (jpegFile != null) {
                    thumbnailCacheRepository.saveThumbnail(file.path, jpegFile)
                    processedCount++
                    Timber.d("ThumbnailPreloadWorker: cached thumbnail for ${file.name}")
                }
            } catch (e: Exception) {
                Timber.w(e, "ThumbnailPreloadWorker: failed to extract thumbnail for ${file.name}")
            }
        }

        // Step 6: Enforce size limit (50% of optimal Glide cache)
        try {
            val maxBytes = calculateOptimalCacheSizeUseCase() * 1024L * 1024L / 2
            val evicted = thumbnailCacheRepository.enforceSizeLimit(maxBytes)
            if (evicted > 0) {
                Timber.i("ThumbnailPreloadWorker: evicted $evicted thumbnail(s) to enforce size limit")
            }
        } catch (e: Exception) {
            Timber.w(e, "ThumbnailPreloadWorker: enforceSizeLimit failed (non-fatal)")
        }

        Timber.i("ThumbnailPreloadWorker: completed - $processedCount new thumbnails cached for resource ${resource.name}")
        return Result.success()
    }
}
