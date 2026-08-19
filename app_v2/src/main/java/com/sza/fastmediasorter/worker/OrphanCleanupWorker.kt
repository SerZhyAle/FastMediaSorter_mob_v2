package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.data.local.db.CachedFileListDao
import com.sza.fastmediasorter.data.local.db.DuplicateHashCacheDao
import com.sza.fastmediasorter.data.local.db.FileMetadataCacheDao
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import com.sza.fastmediasorter.domain.model.CredentialAuditReport
import com.sza.fastmediasorter.domain.model.CredentialStatus
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import com.sza.fastmediasorter.domain.usecase.CredentialAuditor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.UUID

/**
 * Background worker for cleaning up orphaned data records (B5: Security Hardening).
 *
 * Responsibilities:
 * - Delete `cached_file_lists` rows whose parent resource no longer exists.
 * - Delete expired / orphaned entries from `file_metadata_cache` (A5-T8: TTL cleanup).
 * - Log network credentials that have no associated resources (orphan audit).
 *   Credentials are NOT auto-deleted - manual cleanup is intentional to avoid
 *   accidental data loss.
 * - S1649: record when each credential was first seen orphaned, and clear that moment when a
 *   resource references it again. This worker is the only writer of that clock; the deletion
 *   deferral is measured from it.
 *
 * Scheduled as a periodic task by [WorkManagerScheduler].
 */
@HiltWorker
class OrphanCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cachedFileListDao: CachedFileListDao,
    // S1649: a writer here, not a reader. CredentialAuditor is the only thing that decides what
    // "orphaned" means; this DAO exists in the worker solely to wind the orphan clock.
    private val networkCredentialsDao: NetworkCredentialsDao,
    private val credentialAuditor: CredentialAuditor,
    private val fileMetadataCacheDao: FileMetadataCacheDao,
    private val duplicateHashCacheDao: DuplicateHashCacheDao,
    private val resumeStateRepository: ResumeStateRepository,
    private val audioMetadataCacheRepository: AudioMetadataCacheRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "orphan_cleanup_worker"

        /** Metadata cache entries older than this are considered stale and purged. */
        private const val METADATA_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }

    override suspend fun doWork(): Result {
        val correlationId = UUID.randomUUID().toString().take(8)
        Timber.d("[orphan-cleanup/$correlationId] starting orphan cleanup")
        return try {
            cleanOrphanedCaches(correlationId)
            cleanMetadataCache(correlationId)
            cleanDuplicateHashCache(correlationId)
            // S1306: per-window resume-state files outlive their 48 h TTL forever otherwise.
            resumeStateRepository.sweepStaleWindows()
            cleanAudioMetadataCache(correlationId)
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
     * Safe to auto-delete - these are transient cache records only.
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
     * Purge stale and orphaned entries from the per-file metadata cache (A5-T8).
     *
     * Two passes:
     * 1. TTL expiry - entries not refreshed within [METADATA_TTL_MS].
     * 2. Orphan sweep - entries whose parent resource row was deleted (FK cascade
     *    may already handle this, but an explicit pass is a safety net).
     */
    private suspend fun cleanMetadataCache(correlationId: String) {
        val cutoff = System.currentTimeMillis() - METADATA_TTL_MS
        val expired  = fileMetadataCacheDao.deleteExpired(cutoff)
        val orphaned = fileMetadataCacheDao.deleteOrphaned()

        if (expired > 0 || orphaned > 0) {
            Timber.i(
                "[orphan-cleanup/$correlationId] metadata cache purge: " +
                    "$expired expired (TTL=${METADATA_TTL_MS / 86_400_000}d), $orphaned orphaned"
            )
        } else {
            Timber.d("[orphan-cleanup/$correlationId] metadata cache is clean (no expired/orphaned entries)")
        }
    }

    /**
     * S1305: purge the duplicate-detection hash cache. Its rows are keyed by
     * (path, lastModified, fileSize), so an edited or deleted file leaves its row behind forever -
     * the table had no TTL, no orphan sweep, and no place in the Clear-cache flow.
     */
    private suspend fun cleanDuplicateHashCache(correlationId: String) {
        val cutoff = System.currentTimeMillis() - METADATA_TTL_MS
        val expired = duplicateHashCacheDao.deleteExpired(cutoff)
        val orphaned = duplicateHashCacheDao.deleteOrphaned()

        if (expired > 0 || orphaned > 0) {
            Timber.i(
                "[orphan-cleanup/$correlationId] duplicate hash cache purge: $expired expired, $orphaned orphaned"
            )
        } else {
            Timber.d("[orphan-cleanup/$correlationId] duplicate hash cache is clean")
        }
    }

    /**
     * Expire stale audio metadata cache files (TTL) and trim to size budget.
     */
    private fun cleanAudioMetadataCache(correlationId: String) {
        val expired = audioMetadataCacheRepository.cleanupExpired()
        val trimmed = audioMetadataCacheRepository.trimIfNeeded()
        if (expired > 0 || trimmed) {
            Timber.i(
                "[orphan-cleanup/$correlationId] audio metadata cache: " +
                    "$expired expired, trimmed=$trimmed"
            )
        } else {
            Timber.d("[orphan-cleanup/$correlationId] audio metadata cache is clean")
        }
    }

    /**
     * Report on network credentials no longer referenced by any resource, and wind their clock.
     *
     * S1649: the count comes from [CredentialAuditor] rather than from a second query of its own.
     * The worker used to ask the DAO directly and so knew nothing about the grace period - it warned
     * about every unreferenced credential, including ones orphaned minutes earlier, which is the
     * split ADR-2 removes. Deletion stays out of the background path entirely: a password is not
     * recoverable, so it is offered to a human and never taken automatically.
     */
    private suspend fun auditOrphanedCredentials(correlationId: String) {
        val report = credentialAuditor.audit()
        windOrphanClock(correlationId, report)
        val orphaned = report.entries.filter { it.status == CredentialStatus.ORPHANED }
        if (orphaned.isEmpty()) {
            Timber.d("[orphan-cleanup/$correlationId] no orphaned network credentials found")
            return
        }
        Timber.w(
            "[orphan-cleanup/$correlationId] found ${orphaned.size} orphaned credential(s) " +
                "(not referenced by any resource), ${report.eligibleForCleanupCount} past the grace " +
                "period. Credential IDs: ${orphaned.joinToString { it.credentialId }}"
        )
    }

    /**
     * S1649: stamp the moment a credential was first seen orphaned, and clear it once a resource
     * references it again.
     *
     * This is the only writer of that clock. Rows that were already orphaned when the schema 51
     * migration landed carry no moment, and this pass is what gives them one - which is why no
     * backfill statement exists in the migration: a moment derived there from the creation date
     * would restore the very defect the column was added to remove.
     *
     * Both writes are batched, one call per direction, because a per-row update on a table the user
     * never sees is a cost with no benefit.
     */
    private suspend fun windOrphanClock(correlationId: String, report: CredentialAuditReport) {
        val toStamp = report.entries
            .filter { it.status == CredentialStatus.ORPHANED && it.orphanedSince == null }
            .map { it.credentialId }
        val toClear = report.entries
            .filter { it.status != CredentialStatus.ORPHANED && it.orphanedSince != null }
            .map { it.credentialId }

        Timber.d("S1649: orphan clock pass, toStamp=${toStamp.size} toClear=${toClear.size}")
        if (toStamp.isNotEmpty()) {
            networkCredentialsDao.stampOrphanedSince(toStamp, System.currentTimeMillis())
        }
        if (toClear.isNotEmpty()) {
            networkCredentialsDao.clearOrphanedSince(toClear)
        }
        if (toStamp.isNotEmpty() || toClear.isNotEmpty()) {
            Timber.d(
                "[orphan-cleanup/$correlationId] orphan clock: stamped ${toStamp.size}, " +
                    "cleared ${toClear.size}"
            )
        }
    }
}
