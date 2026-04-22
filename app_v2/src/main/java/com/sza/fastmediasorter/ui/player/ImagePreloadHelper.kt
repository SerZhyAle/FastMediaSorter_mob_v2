package com.sza.fastmediasorter.ui.player

import android.net.Uri
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.core.util.MemoryTier
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailData
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.player.render.PrefetchQueue
import com.sza.fastmediasorter.ui.player.render.PrefetchQueueConfig
import com.sza.fastmediasorter.ui.player.render.PriorityPrefetchQueue
import com.sza.fastmediasorter.ui.player.render.RenderModeHint
import com.sza.fastmediasorter.ui.player.render.RenderPriority
import com.sza.fastmediasorter.ui.player.render.RenderTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.io.File

/** Manages adjacent-image preloading for faster navigation in PlayerActivity. */
class ImagePreloadHelper(
    private val binding: ActivityPlayerUnifiedBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val memoryTier: MemoryTier,
    private val callback: Callback,
) {
    interface Callback {
        fun getAdjacentFiles(): List<MediaFile>
        fun getCurrentResource(): com.sza.fastmediasorter.domain.model.MediaResource?
    }

    private val preloadJobs: MutableMap<String, Job> = mutableMapOf()

    val prefetchQueue: PriorityPrefetchQueue = PriorityPrefetchQueue(
        isCongested = { getResourceKey()?.let { ConnectionThrottleManager.isCongested(it) } ?: false }
    ).apply {
        updateConfig(PrefetchQueueConfig(maxDepth = 6, throttleMs = 0L))
    }

    val preloadJobCount: Int get() = synchronized(preloadJobs) { preloadJobs.size }

    fun setSlideshowBias(enabled: Boolean) {
        prefetchQueue.slideshowBias = enabled
        Timber.d("ImagePreloadHelper: Slideshow bias set to $enabled")
    }

    /** Cancels preload jobs for paths no longer adjacent. Returns number cancelled. */
    fun cancelStaleJobsForPaths(activePaths: Set<String>): Int {
        return synchronized(preloadJobs) {
            val stale = preloadJobs.keys.filter { it !in activePaths }
            stale.forEach { path -> preloadJobs.remove(path)?.cancel() }
            stale.size
        }
    }

    fun preloadNextImageIfNeeded() {
        val adjacentFiles = callback.getAdjacentFiles()
        if (adjacentFiles.isEmpty()) {
            Timber.d("ImagePreloadHelper: Preload skipped - no adjacent files")
            return
        }
        val resource = callback.getCurrentResource() ?: run {
            Timber.d("ImagePreloadHelper: Preload skipped - no current resource")
            return
        }
        prefetchQueue.clear()
        val enqueued = prefetchQueue.offerAll(
            adjacentFiles.mapIndexed { index, file ->
                val priority = when (index) {
                    0 -> RenderPriority.NEXT
                    1 -> RenderPriority.PREVIOUS
                    else -> RenderPriority.LOOKAHEAD
                }
                RenderTarget(mediaFile = file, path = file.path, priority = priority, modeHint = RenderModeHint.KEEP_CURRENT)
            }
        )
        Timber.d("ImagePreloadHelper: Starting preload for $enqueued/${adjacentFiles.size} adjacent files (queue=${prefetchQueue.size()})")
        while (true) {
            val target = prefetchQueue.pollNext() ?: break
            preloadAdjacentTarget(target, resource)
        }
        Timber.d("ImagePreloadHelper: Preload initiated via queue-shim")
    }

    fun cleanup() {
        synchronized(preloadJobs) {
            preloadJobs.values.forEach { it.cancel() }
            preloadJobs.clear()
        }
        prefetchQueue.clear()
    }

    private fun getResourceKey(): String? {
        val resource = callback.getCurrentResource() ?: return null
        return when {
            resource.path.startsWith("smb://") -> resource.path.substringBefore("/", resource.path)
            resource.path.startsWith("ftp://") -> "ftp://" + resource.path.substringAfter("://").substringBefore("/")
            resource.path.startsWith("sftp://") -> "sftp://" + resource.path.substringAfter("://").substringBefore("/")
            else -> resource.path
        }
    }

    private fun preloadAdjacentTarget(
        target: RenderTarget,
        resource: com.sza.fastmediasorter.domain.model.MediaResource
    ) {
        val file = target.mediaFile
        Timber.d("ImagePreloadHelper: Preloading ${file.name} (${file.type}) via queue-shim")
        val job = lifecycleScope.launch {
            val actualResourceType = when {
                file.path.startsWith("cloud://") -> ResourceType.CLOUD
                file.path.startsWith("smb://") -> ResourceType.SMB
                file.path.startsWith("sftp://") -> ResourceType.SFTP
                file.path.startsWith("ftp://") -> ResourceType.FTP
                else -> resource.type
            }
            when (actualResourceType) {
                ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP -> preloadNetworkFile(file, resource)
                ResourceType.CLOUD -> preloadCloudFile(file)
                else -> preloadLocalFile(file)
            }
        }
        job.invokeOnCompletion { synchronized(preloadJobs) { preloadJobs.remove(file.path) } }
        synchronized(preloadJobs) { preloadJobs[file.path] = job }
    }

    private suspend fun preloadNetworkFile(
        file: MediaFile,
        resource: com.sza.fastmediasorter.domain.model.MediaResource
    ) {
        val networkData = NetworkFileData(
            path = file.path, credentialsId = resource.credentialsId,
            loadFullImage = true, size = file.size, createdDate = file.createdDate
        )
        val cacheKey = networkData.getCacheKey()
        val preloadMaxDimension = if (memoryTier == MemoryTier.LOW) 1280 else 1920
        try {
            withContext(Dispatchers.IO) {
                val request = Glide.with(binding.root.context.applicationContext)
                    .downloadOnly().load(networkData)
                    .signature(ObjectKey(cacheKey))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .override(preloadMaxDimension, preloadMaxDimension)
                if (memoryTier == MemoryTier.LOW)
                    request.set(com.bumptech.glide.load.Option.memory("decodeFormat"), DecodeFormat.PREFER_RGB_565)
                request.submit().get()
            }
            Timber.d("ImagePreloadHelper: Preload completed for ${file.name}")
        } catch (e: Exception) {
            Timber.d("ImagePreloadHelper: Preload failed for ${file.name}: ${e.message}")
        }
    }

    private suspend fun preloadCloudFile(file: MediaFile) {
        val provider = when {
            file.path.startsWith("cloud://googledrive", ignoreCase = true) ||
                file.path.startsWith("cloud://google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
            file.path.startsWith("cloud://onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
            file.path.startsWith("cloud://dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
            else -> CloudProvider.GOOGLE_DRIVE
        }
        val fileId = when (provider) {
            CloudProvider.DROPBOX -> "/" + file.path.substringAfter("cloud://dropbox").trimStart('/')
            else -> file.path.substringAfterLast('/')
        }
        val cloudData = CloudThumbnailData(
            thumbnailUrl = file.thumbnailUrl ?: "", fileId = fileId,
            loadFullImage = true, cloudProvider = provider
        )
        val cacheKey = "${file.path}_${file.size}"
        val preloadMaxDimension = if (memoryTier == MemoryTier.LOW) 1280 else 1920
        try {
            withContext(Dispatchers.IO) {
                val request = Glide.with(binding.root.context.applicationContext)
                    .downloadOnly().load(cloudData)
                    .signature(ObjectKey(cacheKey))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .override(preloadMaxDimension, preloadMaxDimension)
                if (memoryTier == MemoryTier.LOW)
                    request.set(com.bumptech.glide.load.Option.memory("decodeFormat"), DecodeFormat.PREFER_RGB_565)
                request.submit().get()
            }
            Timber.d("ImagePreloadHelper: Preload completed for ${file.name}")
        } catch (e: Exception) {
            Timber.d("ImagePreloadHelper: Preload failed for ${file.name}: ${e.message}")
        }
    }

    private suspend fun preloadLocalFile(file: MediaFile) {
        val cacheKey = "${file.path}_${file.size}"
        val preloadMaxDimension = if (memoryTier == MemoryTier.LOW) 1280 else 1920
        try {
            withContext(Dispatchers.IO) {
                val loadSource: Any = if (!File(file.path).canRead() && !file.contentUri.isNullOrEmpty()) {
                    Uri.parse(file.contentUri)
                } else File(file.path)
                val request = Glide.with(binding.root.context.applicationContext)
                    .downloadOnly().load(loadSource)
                    .signature(ObjectKey(cacheKey))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .override(preloadMaxDimension, preloadMaxDimension)
                if (memoryTier == MemoryTier.LOW)
                    request.set(com.bumptech.glide.load.Option.memory("decodeFormat"), DecodeFormat.PREFER_RGB_565)
                request.submit().get()
            }
            Timber.d("ImagePreloadHelper: Preload completed for ${file.name}")
        } catch (e: Exception) {
            Timber.d("ImagePreloadHelper: Preload failed for ${file.name}: ${e.message}")
        }
    }
}
