package com.sza.fastmediasorter.data.repository

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.local.db.StreamingCacheDao
import com.sza.fastmediasorter.data.local.db.StreamingCacheEntry
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [StreamingCacheRepository].
 *
 * File access split:
 * - `content://` URIs (scoped-storage MediaStore path) use [android.content.ContentResolver].
 * - Everything else is treated as an absolute filesystem path and accessed via [File].
 *
 * This split is necessary because the offload destination can be either app-private
 * internal storage (direct path) or shared Downloads via MediaStore (API 29+). Both
 * paths round-trip through the same [StreamingCacheEntry.localPath] column as TEXT.
 */
@Singleton
class StreamingCacheRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: StreamingCacheDao
) : StreamingCacheRepository {

    override fun resolveHash(originalUri: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(originalUri.toByteArray())
        // 16 hex chars = 64 bits — ample for the ~hundreds of entries a user will keep.
        return digest.take(8).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    override suspend fun findByHash(resourceHash: String): StreamingCacheEntry? =
        dao.findByHash(resourceHash)

    override suspend fun findByOriginalUri(originalUri: String): StreamingCacheEntry? =
        dao.findByHash(resolveHash(originalUri))

    override suspend fun record(entry: StreamingCacheEntry) {
        dao.upsert(entry)
    }

    override suspend fun touchPlayed(resourceHash: String, now: Long) {
        dao.touchLastPlayedAt(resourceHash, now)
    }

    override suspend fun delete(resourceHash: String): Boolean {
        val entry = dao.findByHash(resourceHash) ?: return false
        deleteLocalFile(entry.localPath)
        dao.deleteByHash(resourceHash)
        return true
    }

    override suspend fun verifyAndPrune(): Int = withContext(Dispatchers.IO) {
        val entries = dao.getAll()
        var pruned = 0
        for (entry in entries) {
            if (!localFileExists(entry.localPath)) {
                dao.deleteByHash(entry.resourceHash)
                pruned++
                Timber.d("StreamingCache: pruned missing file %s", entry.localPath)
            }
        }
        pruned
    }

    override suspend fun findStaleByTtl(now: Long, ttlMs: Long): List<StreamingCacheEntry> {
        val threshold = now - ttlMs
        return dao.findOlderThan(threshold)
    }

    override suspend fun totalSizeBytes(): Long = dao.totalSizeBytes()

    override suspend fun getAll(): List<StreamingCacheEntry> = dao.getAll()

    override fun observeAll(): Flow<List<StreamingCacheEntry>> = dao.observeAll()

    override suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        val entries = dao.getAll()
        for (entry in entries) {
            deleteLocalFile(entry.localPath)
        }
        dao.deleteAll()
        entries.size
    }

    // ── File-side helpers ───────────────────────────────────────────────────────

    private fun localFileExists(localPath: String): Boolean {
        return if (localPath.startsWith("content://")) {
            try {
                val uri = Uri.parse(localPath)
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            } catch (t: Throwable) {
                Timber.d(t, "StreamingCache: content-URI no longer accessible %s", localPath)
                false
            }
        } else {
            File(localPath).exists()
        }
    }

    private fun deleteLocalFile(localPath: String) {
        try {
            if (localPath.startsWith("content://")) {
                val rows = context.contentResolver.delete(Uri.parse(localPath), null, null)
                Timber.d("StreamingCache: ContentResolver deleted %d rows for %s", rows, localPath)
            } else {
                val file = File(localPath)
                if (file.exists() && !file.delete()) {
                    Timber.w("StreamingCache: failed to delete file %s", localPath)
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "StreamingCache: delete failed for %s", localPath)
        }
    }
}
