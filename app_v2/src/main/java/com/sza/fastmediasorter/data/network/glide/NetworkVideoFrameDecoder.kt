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
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Glide ResourceDecoder for extracting video frames from network files (SMB/SFTP/FTP).
 * Uses MediaMetadataRetriever with NetworkMediaDataSource for direct streaming.
 * Caches extracted thumbnails locally for faster subsequent loads.
 */
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
    }

    override fun handles(source: NetworkFileData, options: Options): Boolean {
        val extension = source.path.substringAfterLast('.', "").lowercase()
        
        // Explicitly reject image files to prevent MediaMetadataRetriever errors
        if (extension in IMAGE_EXTENSIONS) {
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
        // Check cache first
        val cachedThumbnail = runBlocking {
            try {
                thumbnailCacheRepository.getCachedThumbnail(source.path)
            } catch (e: Exception) {
                Timber.e(e, "Error checking thumbnail cache for: ${source.path}")
                null
            }
        }
        
        if (cachedThumbnail != null && cachedThumbnail.exists()) {
            Timber.d("Using CACHED thumbnail for: ${source.path.substringAfterLast('/')}")
            return try {
                val bitmap = BitmapFactory.decodeFile(cachedThumbnail.absolutePath)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(
                        FastMediaSorterApp.appContext.resources,
                        bitmap
                    )
                    return BitmapDrawableResource(drawable, bitmapPool)
                } else {
                    Timber.w("Failed to decode cached thumbnail, will re-extract: ${source.path}")
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading cached thumbnail: ${source.path}")
                null
            }
        }
        
        // Check if this file previously failed extraction (unified cache with NetworkFileDataFetcher)
        if (NetworkFileDataFetcher.isVideoFailed(source.path)) {
            Timber.d("Skipping video thumbnail extraction - cached failure: ${source.path.substringAfterLast('/')}")
            return null
        }

        val startTime = System.currentTimeMillis()
        Timber.d("Starting video frame extraction for: ${source.path.substringAfterLast('/')}")

        return try {
            val mediaDataSource = NetworkMediaDataSource(
                path = source.path,
                fileSize = source.size,
                credentialsId = source.credentialsId,
                smbClient = smbClient,
                sftpClient = sftpClient,
                ftpClient = ftpClient,
                credentialsRepository = credentialsRepository
            )

            val bitmap = try {
                extractVideoFrame(mediaDataSource, source.path)
            } finally {
                try {
                    mediaDataSource.close()
                } catch (closeError: Exception) {
                    Timber.w(closeError, "Failed to close NetworkMediaDataSource")
                }
            }

            if (bitmap == null) {
                // Cache this failure using unified cache (shared with NetworkFileDataFetcher)
                NetworkFileDataFetcher.markVideoAsFailed(source.path)
                Timber.w("Failed to extract video thumbnail: ${source.path.substringAfterLast('/')}")
                null
            } else {
                val totalTime = System.currentTimeMillis() - startTime
                Timber.d("Successfully extracted video thumbnail in ${totalTime}ms: ${source.path.substringAfterLast('/')}")
                
                // Save to cache
                runBlocking {
                    try {
                        val cachedFile = saveThumbnailToCache(source.path, bitmap)
                        if (cachedFile != null) {
                            thumbnailCacheRepository.saveThumbnail(source.path, cachedFile)
                            Timber.d("Saved thumbnail to cache: ${source.path.substringAfterLast('/')}")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to save thumbnail to cache: ${source.path}")
                    }
                }
                
                val drawable = BitmapDrawable(
                    FastMediaSorterApp.appContext.resources,
                    bitmap
                )
                BitmapDrawableResource(drawable, bitmapPool)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during video frame extraction: ${source.path.substringAfterLast('/')}")
            // Also mark as failed on exception
            NetworkFileDataFetcher.markVideoAsFailed(source.path)
            null
        }
    }

    private fun extractVideoFrame(mediaDataSource: NetworkMediaDataSource, path: String): Bitmap? {
        val retrieverRef = AtomicReference<MediaMetadataRetriever?>(null)

        // Use executor with timeout to prevent hanging on slow network connections
        val future = extractionExecutor.submit<Bitmap?> {
            val retriever = MediaMetadataRetriever()
            retrieverRef.set(retriever)
            try {
                retriever.setDataSource(mediaDataSource)

                // Extract frame at 1 second (or first frame if video is shorter)
                val frameTime = 1_000_000L // 1 second in microseconds
                retriever.getFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0) // Fallback to first frame
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
            future.get(VIDEO_THUMBNAIL_EXTRACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            // Expected behavior for slow network videos - log without stack trace
            Timber.w("Video thumbnail extraction TIMEOUT after ${VIDEO_THUMBNAIL_EXTRACTION_TIMEOUT_MS}ms for ${path.substringAfterLast('/')} - cancelling")
            retrieverRef.get()?.let { retriever ->
                try {
                    retriever.release()
                    Timber.d("MediaMetadataRetriever force-released after timeout")
                } catch (releaseError: Exception) {
                    Timber.w(releaseError, "Failed to force-release MediaMetadataRetriever after timeout")
                }
            }
            future.cancel(true)
            null
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
            null
        } catch (e: Exception) {
            // Unexpected errors still get full stack trace
            Timber.e(e, "Unexpected error during video frame extraction with timeout")
            if (!future.isDone) {
                future.cancel(true)
            }
            null
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
