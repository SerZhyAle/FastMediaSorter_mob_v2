package com.sza.fastmediasorter.core.util

import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

object CachedMediaMetadataExtractor {

    private val successCount = AtomicInteger(0)
    private val failCount = AtomicInteger(0)

    /** Logs and resets per-session extraction diagnostics. Call once per scan batch. */
    fun logSessionDiagnostics(tag: String = "") {
        val label = if (tag.isNotBlank()) " [$tag]" else ""
        Timber.d("CachedMediaMetadataExtractor$label diagnostics: success=${successCount.getAndSet(0)}, fail=${failCount.getAndSet(0)}")
    }

    suspend fun enrichForCache(file: MediaFile): MediaFile = withContext(Dispatchers.IO) {
        if (file.isDirectory) return@withContext file

        if (!isLocalPath(file.path)) {
            return@withContext file
        }

        if (!File(file.path).exists()) {
            return@withContext file
        }

        return@withContext when (file.type) {
            MediaType.AUDIO -> enrichAudio(file)
            MediaType.VIDEO -> enrichVideo(file)
            MediaType.IMAGE, MediaType.GIF -> enrichImage(file)
            else -> file
        }
    }

    private fun isLocalPath(path: String): Boolean {
        return !path.startsWith("smb://", ignoreCase = true) &&
            !path.startsWith("sftp://", ignoreCase = true) &&
            !path.startsWith("ftp://", ignoreCase = true) &&
            !path.startsWith("cloud://", ignoreCase = true) &&
            !path.startsWith("content://", ignoreCase = true)
    }

    private fun enrichAudio(file: MediaFile): MediaFile {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.path)
                file.copy(
                    artist = file.artist ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = file.album ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    title = file.title ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    duration = file.duration ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                )
            }
        }.onSuccess { successCount.incrementAndGet() }
        .onFailure {
            failCount.incrementAndGet()
            Timber.d("CachedMediaMetadataExtractor audio skipped: ${sanitizePath(file.path)}")
        }.getOrElse { file }
    }

    private fun enrichVideo(file: MediaFile): MediaFile {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.path)
                file.copy(
                    width = file.width ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                    height = file.height ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                    duration = file.duration ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                    videoRotation = file.videoRotation ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
                )
            }
        }.onSuccess { successCount.incrementAndGet() }
        .onFailure {
            failCount.incrementAndGet()
            Timber.d("CachedMediaMetadataExtractor video skipped: ${sanitizePath(file.path)}")
        }.getOrElse { file }
    }

    private fun enrichImage(file: MediaFile): MediaFile {
        return runCatching {
            val exif = ExifInterface(file.path)
            val width = file.width
                ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 }
            val height = file.height
                ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 }
            val exifDateTime = file.exifDateTime ?: parseExifDateTime(
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            )

            file.copy(
                width = width,
                height = height,
                exifDateTime = exifDateTime
            )
        }.onSuccess { successCount.incrementAndGet() }
        .onFailure {
            failCount.incrementAndGet()
            Timber.d("CachedMediaMetadataExtractor image skipped: ${sanitizePath(file.path)}")
        }.getOrElse { file }
    }

    private fun parseExifDateTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null

        val patterns = listOf("yyyy:MM:dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getDefault() }.parse(value)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }

        return null
    }

    private fun sanitizePath(path: String): String {
        return path.takeLast(64)
    }
}
