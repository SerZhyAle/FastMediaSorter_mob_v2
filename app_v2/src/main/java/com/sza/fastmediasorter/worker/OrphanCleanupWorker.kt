package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.data.local.db.CachedFileListDao
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.UUID

/**
 * Background worker for cleaning up orphaned data records (B5: Security Hardening).
 *
 * Responsibilities:
 * - Delete `cached_file_lists` rows whose parent resource no longer exists.
 * - Log network credentials that have no associated resources (orphan audit).
 *   Credentials are NOT auto-deleted — manual cleanup is intentional to avoid
 *   accidental data loss.
 *
 * Scheduled as a periodic task by [WorkManagerScheduler].
 */
@HiltWorker
class OrphanCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cachedFileListDao: CachedFileListDao,
    private val networkCredentialsDao: NetworkCredentialsDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "orphan_cleanup_worker"
    }

    override suspend fun doWork(): Result {
        val correlationId = UUID.randomUUID().toString().take(8)
        Timber.d("[orphan-cleanup/$correlationId] starting orphan cleanup")
        return try {
            cleanOrphanedCaches(correlationId)
            auditOrphanedCredentials(correlationId)
            Timber.i("[orphan-cleanup/$correlationId] completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "[orphan-cleanup/$correlationId] failed")
            Result.failure()
        }
    }

    /**
     * Delete cached file list entries whose parent resource was deleted.
     * Safe to auto-delete — these are transient cache records only.
     */
    private suspend fun cleanOrphanedCaches(correlationId: String) {
        val deleted = cachedFileListDao.deleteOrphaned()
        if (deleted > 0) {
            Timber.i("[orphan-cleanup/$correlationId] removed $deleted orphaned file-list cache entries")
        } else {
            Timber.d("[orphan-cleanup/$correlationId] no orphaned file-list cache entries found")
        }
    }

    /**
     * Log network credentials that are no longer referenced by any resource.
     * Does NOT auto-delete — credentials might be re-associated or the user
     * may want them retained for future resources.
     */
    private suspend fun auditOrphanedCredentials(correlationId: String) {
        val orphaned = networkCredentialsDao.getOrphanedCredentials()
        if (orphaned.isEmpty()) {
            Timber.d("[orphan-cleanup/$correlationId] no orphaned network credentials found")
            return
        }
        Timber.w(
            "[orphan-cleanup/$correlationId] found ${orphaned.size} orphaned credential(s) " +
                "(not referenced by any resource). " +
                "Credential IDs: ${orphaned.joinToString { it.credentialId }}"
        )
    }
}
