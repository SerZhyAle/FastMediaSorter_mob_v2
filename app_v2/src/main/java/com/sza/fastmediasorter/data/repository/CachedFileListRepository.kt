package com.sza.fastmediasorter.data.repository

import android.database.sqlite.SQLiteException
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.data.local.db.CachedFileListDao
import com.sza.fastmediasorter.data.local.db.CachedFileListEntity
import com.sza.fastmediasorter.domain.model.MediaFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedFileListRepository @Inject constructor(
    private val cachedFileListDao: CachedFileListDao
) {
    private val gson = Gson()
    private val mediaFileListType = TypeToken.getParameterized(List::class.java, MediaFile::class.java).type

    // S1307: every patch below is a read-modify-write over one compressed blob. Callers fire these
    // from parallel IO coroutines (batch delete loops, player auto-clean), so two concurrent patches
    // could both decompress the same snapshot and the slower write would silently resurrect the row
    // the other one removed. One mutex per repository serializes all blob patches.
    private val patchMutex = Mutex()

    // ── Compression helpers ───────────────────────────────────────────────────

    private fun compress(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return baos.toByteArray()
    }

    private fun decompress(data: ByteArray): String =
        GZIPInputStream(data.inputStream()).bufferedReader(Charsets.UTF_8).readText()

    private suspend fun discardInvalidCachedFiles(resourceId: Long) {
        try {
            cachedFileListDao.deleteByResourceId(resourceId)
        } catch (exception: SQLiteException) {
            Timber.w(exception, "CachedFileList: failed to discard resource $resourceId")
        }
    }

    private suspend fun readValidCachedFiles(
        resourceId: Long,
        compressedData: ByteArray
    ): MutableList<MediaFile>? {
        return try {
            // A JSON array may itself carry null elements, so the element type stays nullable here
            // instead of letting a null entry surface as a NullPointerException further down.
            val files: List<MediaFile?>? = gson.fromJson(
                decompress(compressedData),
                mediaFileListType
            )
            if (files == null || files.any { it == null || !it.hasRequiredCacheValues() }) {
                discardInvalidCachedFiles(resourceId)
                Timber.w("CachedFileList: discarded invalid snapshot for resource $resourceId")
                null
            } else {
                files.filterNotNull().toMutableList()
            }
        } catch (exception: IOException) {
            discardInvalidCachedFiles(resourceId)
            Timber.w(exception, "CachedFileList: failed to read resource $resourceId")
            null
        } catch (exception: JsonSyntaxException) {
            discardInvalidCachedFiles(resourceId)
            Timber.w(exception, "CachedFileList: failed to read resource $resourceId")
            null
        } catch (_: NullPointerException) {
            // Last net for a mapping-mismatched blob that left a non-null Kotlin property unset. The
            // cause is already known, so the resource id carries more than the throwable would.
            discardInvalidCachedFiles(resourceId)
            Timber.w("CachedFileList: incomplete snapshot for resource $resourceId")
            null
        }
    }

    private fun MediaFile.hasRequiredCacheValues(): Boolean = try {
        name.isNotBlank() && path.isNotBlank() && type.name.isNotBlank()
    } catch (_: NullPointerException) {
        false
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Save the full file list for a resource as a single GZIP-compressed JSON blob. */
    suspend fun saveCachedFiles(
        resourceId: Long,
        files: List<MediaFile>,
        lastScanTimestamp: Long? = null,
        lastModifiedFolder: Long? = null
    ) {
        if (files.size > 1_000_000) {
            Timber.w("CachedFileList: file count ${files.size} exceeds limit for resource $resourceId - skipping")
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
            val entity = cachedFileListDao.getByResourceId(resourceId)
            if (entity == null) {
                null
            } else {
                val files = readValidCachedFiles(resourceId, entity.compressedData)
                if (files == null) {
                    null
                } else {
                    Timber.d("CachedFileList: loaded ${files.size} files for resource $resourceId")
                    files
                }
            }
        } catch (exception: SQLiteException) {
            Timber.e(exception, "CachedFileList: failed to load files for resource $resourceId")
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
    suspend fun updateFile(resourceId: Long, oldPath: String, newFile: MediaFile) = patchMutex.withLock {
        try {
            val entity = cachedFileListDao.getByResourceId(resourceId) ?: return
            val files = readValidCachedFiles(resourceId, entity.compressedData) ?: return
            val idx = files.indexOfFirst { it.path == oldPath }
            if (idx < 0) {
                Timber.w("CachedFileList: updateFile - $oldPath not found in resource $resourceId")
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
    suspend fun deleteFile(resourceId: Long, filePath: String) = deleteFiles(resourceId, listOf(filePath))

    /**
     * Patch the cached list after a batch delete/move: decompress → remove entries → recompress.
     * No-op if no snapshot is cached.
     *
     * S1307: callers used to loop [deleteFile] per path, paying a full decompress/recompress of the
     * whole snapshot for every single file - and racing each other while doing it.
     */
    suspend fun deleteFiles(resourceId: Long, filePaths: Collection<String>) = patchMutex.withLock {
        if (filePaths.isEmpty()) return@withLock
        try {
            val entity = cachedFileListDao.getByResourceId(resourceId) ?: return@withLock
            val files = readValidCachedFiles(resourceId, entity.compressedData) ?: return@withLock
            val victims = filePaths.toHashSet()
            val removed = files.removeIf { it.path in victims }
            if (!removed) {
                Timber.w("CachedFileList: deleteFiles - no match for ${filePaths.size} path(s) in $resourceId")
                return@withLock
            }
            val compressed = compress(gson.toJson(files))
            cachedFileListDao.insertOrReplace(
                entity.copy(compressedData = compressed, fileCount = files.size)
            )
            Timber.d("CachedFileList: removed ${filePaths.size} path(s) from resource $resourceId")
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: failed to delete files from resource $resourceId")
        }
    }
}
