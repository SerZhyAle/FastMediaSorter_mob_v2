package com.sza.fastmediasorter.core.util

import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.sza.fastmediasorter.data.local.db.FileMetadataCacheDao
import com.sza.fastmediasorter.data.local.db.FileMetadataCacheEntity
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.MetadataState
import com.sza.fastmediasorter.domain.model.ResourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Extracts and caches media file metadata (A5-T5..T8).
 * Provided as singleton via [com.sza.fastmediasorter.core.di.DatabaseModule].
 */
class CachedMediaMetadataExtractor(
    private val fileMetadataCacheDao: FileMetadataCacheDao
) {

    private val successCount = AtomicInteger(0)
    private val failCount = AtomicInteger(0)
    private val cacheHitCount = AtomicInteger(0)

    /** Logs and resets per-session extraction diagnostics. */
    fun logSessionDiagnostics(tag: String = "") {
        val label = if (tag.isNotBlank()) " [$tag]" else ""
        Timber.d("CachedMediaMetadataExtractor$label: success=${successCount.getAndSet(0)}, hits=${cacheHitCount.getAndSet(0)}, fail=${failCount.getAndSet(0)}")
    }

    /**
     * Enriches a list of files with metadata, using Room cache when available and valid.
     *
     * S0248 Phase 3 retry policy applied to cached rows:
     *  - `COMPLETE` → use as-is, no re-extraction.
     *  - `PARTIAL`  → re-attempt extraction (every scan).
     *  - `BROKEN`   → keep cached payload, do NOT re-attempt unless [forceRefresh] is true
     *    (set by an explicit user-triggered refresh action).
     *
     * Legacy rows that predate migration 30→31 are persisted as `COMPLETE` via the SQL
     * DEFAULT (see [com.sza.fastmediasorter.data.local.db.AppDatabase.MIGRATION_30_31]).
     */
    suspend fun enrichBatch(
        resourceId: Long,
        resourceType: ResourceType,
        credentialsId: String?,
        files: List<MediaFile>,
        forceRefresh: Boolean = false
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val cache = fileMetadataCacheDao.getAllForResource(resourceId).associateBy { it.filePath }
        val toUpdate = mutableListOf<FileMetadataCacheEntity>()

        val enriched = files.map { file ->
            if (file.isDirectory || !isLocalPath(file.path)) return@map file

            val cached = cache[file.path]
            val cachedValid = cached != null &&
                cached.lastModified == file.createdDate &&
                cached.fileSize == file.size
            val cachedState = cached?.metadataState?.let { runCatching { MetadataState.valueOf(it) }.getOrDefault(MetadataState.COMPLETE) }

            // S0248 Phase 3: decide whether the cached row may be reused as-is, or whether
            // we must re-extract. PARTIAL always re-extracts. BROKEN re-extracts only when
            // the caller opted into a forced refresh.
            val reuseCached = cachedValid && when (cachedState) {
                MetadataState.COMPLETE -> true
                MetadataState.PARTIAL -> false
                MetadataState.BROKEN -> !forceRefresh
                null -> true // defensive: treat unknown as COMPLETE
            }

            if (reuseCached) {
                cacheHitCount.incrementAndGet()
                file.copy(
                    duration = cached?.durationMs ?: file.duration,
                    width = cached?.width ?: file.width,
                    height = cached?.height ?: file.height,
                    videoRotation = cached?.videoRotation ?: file.videoRotation,
                    exifDateTime = cached?.exifDateTime ?: file.exifDateTime,
                    artist = cached?.artist ?: file.artist,
                    album = cached?.album ?: file.album,
                    title = cached?.title ?: file.title,
                    metadataState = cachedState ?: MetadataState.COMPLETE
                )
            } else {
                val freshlyEnriched = enrichInternal(file)
                // Local extractor never times out (no I/O budget on disk), so the only two
                // outcomes are: enrichment changed the row (= COMPLETE) or it returned the
                // input unchanged. The latter could mean "no metadata available for the
                // type" (e.g. plain text) - recorded as COMPLETE - OR an exception
                // suppressed by runCatching → also reaches us as `file`. We treat that
                // case as COMPLETE here; the dedicated PARTIAL/BROKEN states are produced
                // by network scanners with a real per-file timeout.
                val finalFile = freshlyEnriched.copy(metadataState = MetadataState.COMPLETE)
                if (finalFile != file) {
                    toUpdate.add(mapToEntity(resourceId, resourceType, credentialsId, finalFile))
                }
                finalFile
            }
        }

        if (toUpdate.isNotEmpty()) {
            fileMetadataCacheDao.upsertAll(toUpdate)
        }
        enriched
    }

    private fun isLocalPath(path: String): Boolean {
        return !path.startsWith("smb://", ignoreCase = true) &&
            !path.startsWith("sftp://", ignoreCase = true) &&
            !path.startsWith("ftp://", ignoreCase = true) &&
            !path.startsWith("cloud://", ignoreCase = true) &&
            !path.startsWith("content://", ignoreCase = true)
    }

    private suspend fun enrichInternal(file: MediaFile): MediaFile {
        if (!File(file.path).exists()) return file

        return when (file.type) {
            MediaType.AUDIO -> enrichAudio(file)
            MediaType.VIDEO -> enrichVideo(file)
            MediaType.IMAGE, MediaType.GIF -> enrichImage(file)
            else -> file
        }
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
        .onFailure { failCount.incrementAndGet() }
        .getOrElse { file }
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
        .onFailure { failCount.incrementAndGet() }
        .getOrElse { file }
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
        .onFailure { failCount.incrementAndGet() }
        .getOrElse { file }
    }

    private fun mapToEntity(
        resourceId: Long,
        resourceType: ResourceType,
        credentialsId: String?,
        file: MediaFile
    ): FileMetadataCacheEntity {
        return FileMetadataCacheEntity(
            resourceId = resourceId,
            filePath = file.path,
            provider = resourceType.name,
            credentialsId = credentialsId,
            lastModified = file.createdDate,
            fileSize = file.size,
            cachedAt = System.currentTimeMillis(),
            thumbnailPath = null, // Handled separately or in future task
            durationMs = file.duration,
            width = file.width,
            height = file.height,
            videoRotation = file.videoRotation,
            exifDateTime = file.exifDateTime,
            exifJson = null, // Could serialize full EXIF here if needed
            artist = file.artist,
            album = file.album,
            title = file.title,
            metadataState = file.metadataState.name
        )
    }

    private fun parseExifDateTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val patterns = listOf("yyyy:MM:dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
        for (pattern in patterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US).apply { 
                    timeZone = TimeZone.getDefault() 
                }.parse(value)?.time
            } catch (e: Exception) { /* continue */ }
        }
        return null
    }
}
