package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.sza.fastmediasorter.core.constants.AppConstants
import android.util.Log
import androidx.core.net.toUri
import com.sza.fastmediasorter.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject

/**
 * Video metadata extracted from video files
 */
data class VideoMetadata(
    val duration: Long? = null, // Duration in milliseconds
    val width: Int? = null, // Video width in pixels
    val height: Int? = null, // Video height in pixels
    val codec: String? = null, // Video codec name (e.g., "avc1", "vp9", "hevc")
    val bitrate: Int? = null, // Video bitrate in bits per second
    val frameRate: Float? = null, // Video frame rate (fps)
    val rotation: Int? = null // Video rotation angle (0, 90, 180, 270 degrees)
)

/**
 * Use case to extract video metadata from video files
 * Runs on IO dispatcher to avoid blocking UI thread
 */
class ExtractVideoMetadataUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "ExtractVideoMetadataUC"

    /**
     * Extract video metadata from local file
     * @param filePath Absolute file path
     * @return VideoMetadata with extracted fields or nulls if extraction failed
     */
    suspend fun extractFromFile(filePath: String): VideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                Log.w(tag, "File does not exist or not readable: $filePath")
                return@withContext VideoMetadata()
            }

            withTimeout(AppConstants.METADATA_EXTRACTION_TIMEOUT_MS) {
                retriever.setDataSource(filePath)
                extractMetadata(retriever)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to extract video metadata from file: $filePath", e)
            VideoMetadata()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(tag, "Failed to release MediaMetadataRetriever", e)
            }
        }
    }

    /**
     * Extract video metadata from Uri (content:// or file://)
     * @param uri Content Uri of the video
     * @return VideoMetadata with extracted fields or nulls if extraction failed
     */
    suspend fun extractFromUri(uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            withTimeout(AppConstants.METADATA_EXTRACTION_TIMEOUT_MS) {
                retriever.setDataSource(context, uri)
                extractMetadata(retriever)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to extract video metadata from uri: $uri", e)
            VideoMetadata()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(tag, "Failed to release MediaMetadataRetriever", e)
            }
        }
    }

    /**
     * Extract video metadata from network URL or custom data source
     * Note: This may be slow for network files, consider using cached/downloaded file
     * @param dataSource Data source string (URL, file path, etc.)
     * @param headers Optional HTTP headers for network sources
     * @return VideoMetadata with extracted fields or nulls if extraction failed
     */
    suspend fun extractFromDataSource(
        dataSource: String,
        headers: Map<String, String>? = null
    ): VideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            withTimeout(AppConstants.METADATA_EXTRACTION_TIMEOUT_MS) {
                if (headers != null) {
                    retriever.setDataSource(dataSource, headers)
                } else {
                    retriever.setDataSource(dataSource)
                }
                extractMetadata(retriever)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to extract video metadata from data source: $dataSource", e)
            VideoMetadata()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(tag, "Failed to release MediaMetadataRetriever", e)
            }
        }
    }

    /**
     * Common method to extract metadata from MediaMetadataRetriever
     */
    private fun extractMetadata(retriever: MediaMetadataRetriever): VideoMetadata {
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()

        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()

        // Extract mime type as codec indicator (METADATA_KEY_VIDEO_CODEC doesn't exist in API)
        val codec = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toIntOrNull()

        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()

        // Extract frame rate (available on API 23+)
        val frameRate = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to extract frame rate", e)
            null
        }

        return VideoMetadata(
            duration = duration,
            width = width,
            height = height,
            codec = codec,
            bitrate = bitrate,
            frameRate = frameRate,
            rotation = rotation
        )
    }

    /**
     * Check if file type supports video metadata extraction
     */
    fun supportsVideoMetadata(mediaType: MediaType): Boolean {
        return mediaType == MediaType.VIDEO
    }

    /**
     * Check if file extension typically supports video metadata
     */
    fun supportsVideoMetadata(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in setOf(
            "mp4", "mkv", "mov", "webm", "3gp", "flv", "wmv", "m4v",
            "avi", "mpg", "mpeg", "ts", "m2ts", "mts", "ogv", "vob"
        )
    }
}
