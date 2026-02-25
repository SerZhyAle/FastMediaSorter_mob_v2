package com.sza.fastmediasorter.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.data.local.db.CachedFileListDao
import com.sza.fastmediasorter.data.local.db.CachedFileListEntity
import com.sza.fastmediasorter.domain.model.MediaFile
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedFileListRepository @Inject constructor(
    private val cachedFileListDao: CachedFileListDao
) {
    private val gson = Gson()
    private val mediaFileListType = object : TypeToken<List<MediaFile>>() {}.type

    // ── Compression helpers ───────────────────────────────────────────────────

    private fun compress(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return baos.toByteArray()
    }

    private fun decompress(data: ByteArray): String =
        GZIPInputStream(data.inputStream()).bufferedReader(Charsets.UTF_8).readText()

    // ── Public API ────────────────────────────────────────────────────────────

    /** Save the full file list for a resource as a single GZIP-compressed JSON blob. */
    suspend fun saveCachedFiles(
        resourceId: Long,
        files: List<MediaFile>,
        lastScanTimestamp: Long? = null,
        lastModifiedFolder: Long? = null
    ) {
        if (files.size > 1_000_000) {
            Timber.w("CachedFileList: file count ${files.size} exceeds limit for resource $resourceId — skipping")
            return
        }
        try {
            val compressed = compress(gson.toJson(files))
            val entity = CachedFileListEntity(
                resourceId = resourceId,
                compressedData = compressed,
                fileCount = files.size,
                lastScanTimestamp = lastScanTimestamp,
                lastModifiedFolder = lastModifiedFolder
            )
            // insertOrReplace handles the unique index on resourceId
            cachedFileListDao.insertOrReplace(entity)
            Timber.d("CachedFileList: saved ${files.size} files (${compressed.size} bytes) for resource $resourceId (lastScan=$lastScanTimestamp)")
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: failed to save files for resource $resourceId")
            throw e
        }
    }

    /** Returns the raw database entity for a resource (used for A5 cache validation). */
    suspend fun getEntityByResourceId(resourceId: Long): CachedFileListEntity? =
        cachedFileListDao.getByResourceId(resourceId)

    /** Load the file list for a resource, or return null if nothing is cached. */
    suspend fun getCachedFiles(resourceId: Long): List<MediaFile>? {
        return try {
            val entity = cachedFileListDao.getByResourceId(resourceId) ?: return null
            val files: List<MediaFile> = gson.fromJson(decompress(entity.compressedData), mediaFileListType)
            Timber.d("CachedFileList: loaded ${files.size} files for resource $resourceId")
            files
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: failed to load files for resource $resourceId")
            null
        }
    }

    /** Delete the cached snapshot for a specific resource. */
    suspend fun deleteCachedFiles(resourceId: Long) {
        cachedFileListDao.deleteByResourceId(resourceId)
        Timber.d("CachedFileList: deleted cache for resource $resourceId")
    }

    /** Delete ALL cached snapshots (called when the user clears cache). */
    suspend fun deleteAllCachedFiles() {
        cachedFileListDao.deleteAll()
        Timber.d("CachedFileList: deleted all cached file lists")
    }

    /** Returns true if a cached snapshot exists for the resource. */
    suspend fun hasCachedFiles(resourceId: Long): Boolean =
        cachedFileListDao.getByResourceId(resourceId) != null

    /**
     * Patch the cached list after a rename: decompress → replace entry → recompress.
     * No-op if no snapshot is cached.
     */
    suspend fun updateFile(resourceId: Long, oldPath: String, newFile: MediaFile) {
        try {
            val entity = cachedFileListDao.getByResourceId(resourceId) ?: return
            val files: MutableList<MediaFile> =
                gson.fromJson(decompress(entity.compressedData), mediaFileListType)
            val idx = files.indexOfFirst { it.path == oldPath }
            if (idx < 0) {
                Timber.w("CachedFileList: updateFile — $oldPath not found in resource $resourceId")
                return
            }
            files[idx] = newFile
            val compressed = compress(gson.toJson(files))
            cachedFileListDao.insertOrReplace(
                entity.copy(compressedData = compressed, fileCount = files.size)
            )
            Timber.d("CachedFileList: updated $oldPath → ${newFile.path} in resource $resourceId")
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: failed to update file in resource $resourceId")
        }
    }

    /**
     * Patch the cached list after a delete/move: decompress → remove entry → recompress.
     * No-op if no snapshot is cached.
     */
    suspend fun deleteFile(resourceId: Long, filePath: String) {
        try {
            val entity = cachedFileListDao.getByResourceId(resourceId) ?: return
            val files: MutableList<MediaFile> =
                gson.fromJson(decompress(entity.compressedData), mediaFileListType)
            val removed = files.removeIf { it.path == filePath }
            if (!removed) {
                Timber.w("CachedFileList: deleteFile — $filePath not found in resource $resourceId")
                return
            }
            val compressed = compress(gson.toJson(files))
            cachedFileListDao.insertOrReplace(
                entity.copy(compressedData = compressed, fileCount = files.size)
            )
            Timber.d("CachedFileList: removed $filePath from resource $resourceId")
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: failed to delete file from resource $resourceId")
        }
    }
}

