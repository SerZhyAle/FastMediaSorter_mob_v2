package com.sza.fastmediasorter.data.network.glide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.drawable.DrawableResource
import com.sza.fastmediasorter.FastMediaSorterApp
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import com.sza.fastmediasorter.utils.GlideCacheStats
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * Glide ResourceDecoder for extracting video frames from network files (SMB/SFTP/FTP).
 * Uses MediaMetadataRetriever with NetworkMediaDataSource for direct streaming.
 * Caches extracted thumbnails locally for faster subsequent loads.
 *
 * Fix 1: before marking a file as failed, checks ThumbnailCache — if a concurrent thread
 *         already saved a valid thumbnail, the failed-cache entry is suppressed.
 * Fix 2: per-path deduplication via ConcurrentHashMap<path, CompletableFuture<Boolean>>.
 *         Only one extraction runs at a time per file; secondary Glide threads wait for
 *         the primary result, then serve from ThumbnailCache.
 * S0060: transient SMB failures (stale-share race, timeout during active playback) are NOT
 *         written to the permanent failed-cache. They are recorded in a separate transient map
 *         and cleared when playback stops, allowing automatic retry on next scroll-in.
 */

/** Outcome of a single video-frame extraction attempt. S0060. */
private data class ExtractionOutcome(val bitmap: Bitmap?, val isTimeout: Boolean = false)
class NetworkVideoFrameDecoder(
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val thumbnailCacheRepository: ThumbnailCacheRepository,
    private val bitmapPool: BitmapPool
) : ResourceDecoder<NetworkFileData, Drawable> {

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mov", "avi", "mkv", "webm", "3gp", "flv", "wmv", "m4v", "mpg", "mpeg"
        )

        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
        )

        // Timeout for video thumbnail extraction (10 seconds max)
        private const val VIDEO_THUMBNAIL_EXTRACTION_TIMEOUT_MS = 10_000L

        // Executor for timeout-controlled operations
        private val extractionExecutor = Executors.newCachedThreadPool()

        // Fix 2: per-path deduplication — only one extraction per file path runs at a time.
        // Future<Boolean>: true = success (ThumbnailCache populated), false = failed/timeout.
        private val inFlightExtractions = ConcurrentHashMap<String, CompletableFuture<Boolean>>()
    }

    override fun handles(source: NetworkFileData, options: Options): Boolean {
        val extension = source.path.substringAfterLast('.', "").lowercase()
        // Explicitly reject image files to prevent MediaMetadataRetriever errors
        if (extension in IMAGE_EXTENSIONS) return false
        // S0063: skip formats known to fail on network streams to avoid 10-second timeout per file
        if (NetworkThumbnailExtractionPolicy.shouldSkipNetworkExtraction(extension)) {
            Timber.v("[scope=thumbnail S0063] Blocked network format '$extension': ${source.path.substringAfterLast('/')}")
            return false
        }
        return extension in VIDEO_EXTENSIONS
    }

    override fun decode(
        source: NetworkFileData,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Drawable>? {
        val fileName = source.path.substringAfterLast('/')

        // 1. Always check ThumbnailCache first — serves even if file is in the failed cache
        loadFromThumbnailCache(source.path, fileName)?.let { return it }

        // Guard: no LAN permission — skip extraction without caching failure
        if (!PermissionHelper.hasLocalNetworkPermission(FastMediaSorterApp.appContext)) {
            return null
        }

        // 2. Skip extraction if previously failed (cache miss + failed = no retry this session).
        //    S0060: transient failures (SMB share-race) are stored separately and cleared when
        //    playback stops. If playback has since ended, clear the transient entry and proceed.
        if (NetworkFileDataFetcher.isVideoFailed(source.path)) {
            if (!NetworkFileDataFetcher.isVideoPermanentlyFailed(source.path)) {
                // Transiently failed: check if playback is still active for this resource. S0066.
                val resourceKey = extractNetworkResourceKey(source.path)
                if (resourceKey != null && ConnectionThrottleManager.isVideoPlayerActiveForResource(resourceKey)) {
                    Timber.v("[scope=thumbnail S0066 protocol=${source.path.substringBefore("://")} resource=$resourceKey] Skipping: transient failure during active playback: $fileName")
                    return null
                } else {
                    // Playback stopped (or non-network) — clear transient failure and allow retry
                    NetworkFileDataFetcher.clearTransientFailure(source.path)
                    Timber.d("[scope=thumbnail S0060] Cleared transient failure (playback ended): $fileName")
                }
            } else {
                Timber.v("Skipping video thumbnail extraction - cached failure: $fileName")
                return null
            }
        }

        // 3. Fix 2: Deduplicate — only one extraction per path at a time
        val ourFuture = CompletableFuture<Boolean>()
        val existingFuture = inFlightExtractions.putIfAbsent(source.path, ourFuture)

        if (existingFuture != null) {
            // Secondary thread: another extraction is already in flight — wait for its result
            Timber.d("Dedup: waiting for in-flight extraction of: $fileName")
            val succeeded = try {
                // Wait slightly longer than the primary's own extraction timeout
                existingFuture.get(VIDEO_THUMBNAIL_EXTRACTION_TIMEOUT_MS + 2_000L, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                Timber.d("Dedup: wait timed out/interrupted for: $fileName")
                false
            }
            // Primary saved to ThumbnailCache before completing the future — serve from there
            return if (succeeded) loadFromThumbnailCache(source.path, fileName) else null
        }

        // 4. Primary thread: perform the actual extraction
        val startTime = System.currentTimeMillis()
        Timber.v("Starting video frame extraction for: $fileName")

        val mediaDataSource = NetworkMediaDataSource(
            path = source.path,
            fileSize = source.size,
            credentialsId = source.credentialsId,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            credentialsRepository = credentialsRepository
        )

        var extractionSucceeded = false
        return try {
            val outcome = try {
                extractVideoFrame(mediaDataSource, source.path)
            } finally {
                try {
                    mediaDataSource.close()
                } catch (closeError: Exception) {
                    Timber.w(closeError, "Failed to close NetworkMediaDataSource")
                }
            }
            // Read stale-share flag AFTER close() — set by NetworkMediaDataSource.readAt(). S0060.
            val isStaleShare = mediaDataSource.encounteredStaleShare

            if (outcome.bitmap != null) {
                val elapsed = System.currentTimeMillis() - startTime
                Timber.v("Successfully extracted video thumbnail in ${elapsed}ms: $fileName")

                // Save to cache BEFORE completing the future so secondary waiters can read immediately
                runBlocking {
                    try {
                        val cachedFile = saveThumbnailToCache(source.path, outcome.bitmap)
                        if (cachedFile != null) {
                            thumbnailCacheRepository.saveThumbnail(source.path, cachedFile)
                            Timber.v("Saved thumbnail to cache: $fileName")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to save thumbnail to cache: $fileName")
                    }
                }

                extractionSucceeded = true
                val drawable = BitmapDrawable(FastMediaSorterApp.appContext.resources, outcome.bitmap)
                BitmapDrawableResource(drawable, bitmapPool)
            } else {
                // S0066: universal transient classification across SMB / SFTP / FTP.
                val resourceKey = extractNetworkResourceKey(source.path)
                val playbackActive = resourceKey != null &&
                    ConnectionThrottleManager.isVideoPlayerActiveForResource(resourceKey)
                val transientReason = mediaDataSource.transientFailureReason
                    ?: if (isStaleShare) TransientReason.STALE_SHARE else null
                // Timeout is transient only when playback was active for the same resource.
                val isTransient = transientReason != null ||
                    (outcome.isTimeout && playbackActive)
                val protocol = source.path.substringBefore("://", missingDelimiterValue = "local")
                val failureClass = transientReason?.name?.lowercase()
                    ?: if (outcome.isTimeout) "timeout" else "null-frame"
                Timber.w("[scope=thumbnail S0066 protocol=$protocol resource=${resourceKey ?: "n/a"} failureClass=$failureClass playbackActive=$playbackActive] Extraction failed: $fileName")

                if (isTransient) {
                    NetworkFileDataFetcher.markVideoAsTransientlyFailed(source.path)
                } else {
                    val cacheCheck = runBlocking {
                        try { thumbnailCacheRepository.getCachedThumbnail(source.path) } catch (e: Exception) { null }
                    }
                    if (cacheCheck == null || !cacheCheck.exists()) {
                        NetworkFileDataFetcher.markVideoAsFailed(source.path)
                    } else {
                        Timber.d("Not marking as failed — ThumbnailCache already has entry: $fileName")
                    }
                }
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during video frame extraction: $fileName")
            NetworkFileDataFetcher.markVideoAsFailed(source.path)
            null
        } finally {
            // Always signal secondary waiters and remove from the in-flight map
            ourFuture.complete(extractionSucceeded)
            inFlightExtractions.remove(source.path, ourFuture)
        }
    }

    private fun loadFromThumbnailCache(path: String, fileName: String): Resource<Drawable>? {
        val cached = runBlocking {
            try {
                thumbnailCacheRepository.getCachedThumbnail(path)
            } catch (e: Exception) {
                Timber.e(e, "Error checking thumbnail cache for: $path")
                null
            }
        }
        if (cached == null || !cached.exists()) return null
        Timber.v("Using CACHED thumbnail for: $fileName")
        return try {
            val bitmap = BitmapFactory.decodeFile(cached.absolutePath)
            if (bitmap != null) {
                GlideCacheStats.recordThumbnailRepoHit()
                val drawable = BitmapDrawable(FastMediaSorterApp.appContext.resources, bitmap)
                BitmapDrawableResource(drawable, bitmapPool)
            } else {
                Timber.w("Failed to decode cached thumbnail, will re-extract: $path")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading cached thumbnail: $path")
            null
        }
    }

    /**
     * Extracts a single video frame via MediaMetadataRetriever with a hard watchdog timeout.
     * Returns [ExtractionOutcome.isTimeout]=true when the watchdog fires, so the caller can
     * classify the failure as transient when SMB playback is concurrently active. S0060.
     */
    private fun extractVideoFrame(mediaDataSource: NetworkMediaDataSource, path: String): ExtractionOutcome {
        val retrieverRef = AtomicReference<MediaMetadataRetriever?>(null)

        // Use executor with timeout to prevent hanging on slow network connections
        val future = extractionExecutor.submit<Bitmap?> {
            val retriever = MediaMetadataRetriever()
            retrieverRef.set(retriever)
            try {
                retriever.setDataSource(mediaDataSource)

                // Extract frame at 1 second; if null the file header is unreadable — skip fallback
                // to avoid a second system-level "videoFrame is a NULL pointer" warning.
                val frameTime = 1_000_000L // 1 second in microseconds
                val frame = retriever.getFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame == null) {
                    Timber.w("getFrameAtTime returned null for ${path.substringAfterLast('/')}, skipping fallback")
                }
                frame
            } catch (e: Exception) {
                if (isExpectedFrameExtractionFailure(e)) {
                    Timber.w("Video frame extraction skipped for ${path.substringAfterLast('/')} - ${e.message}")
                } else {
                    Timber.w("Video frame extraction failed for ${path.substringAfterLast('/')} - ${e.javaClass.simpleName}: ${e.message}")
                }
                null
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to release MediaMetadataRetriever")
                } finally {
                    retrieverRef.compareAndSet(retriever, null)
                }
            }
        }

        return try {
            ExtractionOutcome(future.get(VIDEO_THUMBNAIL_EXTRACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        } catch (e: TimeoutException) {
            // Expected behavior for slow network videos - log without stack trace
            Timber.w("[scope=thumbnail failureClass=timeout] Extraction TIMEOUT after ${VIDEO_THUMBNAIL_EXTRACTION_TIMEOUT_MS}ms for ${path.substringAfterLast('/')} - cancelling")
            retrieverRef.get()?.let { retriever ->
                try {
                    retriever.release()
                    Timber.d("MediaMetadataRetriever force-released after timeout")
                } catch (releaseError: Exception) {
                    Timber.w(releaseError, "Failed to force-release MediaMetadataRetriever after timeout")
                }
            }
            future.cancel(true)
            // isTimeout=true so decode() can check if SMB playback was active and classify as transient
            ExtractionOutcome(bitmap = null, isTimeout = true)
        } catch (e: InterruptedException) {
            // Expected during cancellation - log at debug level without stack trace
            Timber.d("Video thumbnail extraction INTERRUPTED - cancelling: ${path.substringAfterLast('/')}")
            retrieverRef.get()?.let { retriever ->
                try {
                    retriever.release()
                    Timber.d("MediaMetadataRetriever force-released after interruption")
                } catch (releaseError: Exception) {
                    Timber.w(releaseError, "Failed to force-release MediaMetadataRetriever after interruption")
                }
            }
            future.cancel(true)
            Thread.currentThread().interrupt()
            ExtractionOutcome(bitmap = null)
        } catch (e: Exception) {
            // Unexpected errors still get full stack trace
            Timber.e(e, "Unexpected error during video frame extraction with timeout")
            if (!future.isDone) {
                future.cancel(true)
            }
            ExtractionOutcome(bitmap = null)
        }
    }

    /**
     * Save extracted thumbnail bitmap to cache directory.
     * @return Cached file or null if save failed
     */
    private fun saveThumbnailToCache(filePath: String, bitmap: Bitmap): File? {
        return try {
            val cacheDir = File(FastMediaSorterApp.appContext.cacheDir, "thumbnails")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Generate unique filename from path hash
            val hash = MessageDigest.getInstance("MD5")
                .digest(filePath.toByteArray())
                .joinToString("") { "%02x".format(it) }

            val cachedFile = File(cacheDir, "$hash.jpg")

            FileOutputStream(cachedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            cachedFile
        } catch (e: Exception) {
            Timber.e(e, "Failed to save thumbnail to cache")
            null
        }
    }

    /**
     * Custom Resource wrapper for BitmapDrawable that properly recycles the bitmap
     */
    @Suppress("OVERRIDE_DEPRECATION")
    private class BitmapDrawableResource(
        drawable: BitmapDrawable,
        private val bitmapPool: BitmapPool
    ) : DrawableResource<Drawable>(drawable) {

        override fun getResourceClass(): Class<Drawable> = Drawable::class.java

        override fun getSize(): Int {
            val bitmapDrawable = (this as DrawableResource<Drawable>).get() as? BitmapDrawable ?: return 0
            val bmp = bitmapDrawable.bitmap ?: return 0
            return bmp.height * bmp.rowBytes
        }

        override fun recycle() {
            val bitmapDrawable = (this as DrawableResource<Drawable>).get() as? BitmapDrawable ?: return
            val bmp = bitmapDrawable.bitmap ?: return
            bitmapPool.put(bmp)
        }
    }

    private fun isExpectedFrameExtractionFailure(error: Exception): Boolean {
        if (error is InterruptedException || error is java.util.concurrent.CancellationException) {
            return true
        }

        val message = error.message.orEmpty()
        if (message.contains("setDataSourceCallback failed", ignoreCase = true) ||
            message.contains("status = 0x80000000", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true)
        ) {
            return true
        }

        val cause = error.cause
        return cause is InterruptedException ||
            cause is java.util.concurrent.CancellationException ||
            cause?.message?.contains("setDataSourceCallback failed", ignoreCase = true) == true
    }
}

/**
 * Dummy InputStream wrapper for ResourceDecoder interface.
 * We don't actually use InputStream since NetworkFileData contains all info.
 */
class NetworkFileDataInputStream(val data: NetworkFileData) : InputStream() {
    override fun read(): Int = -1
}
