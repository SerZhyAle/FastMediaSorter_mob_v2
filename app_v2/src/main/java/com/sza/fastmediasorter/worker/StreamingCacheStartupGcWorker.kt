package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Startup-time GC for the streaming-offload cache (see spec §5.7, trigger 3).
 *
 * Two passes:
 * 1. `verifyAndPrune` — drop DB rows whose local file was deleted out-of-band.
 * 2. TTL eviction — delete rows (and files) whose `lastPlayedAt` is older than
 *    the configured TTL.
 *
 * TTL is read from worker input data (`KEY_TTL_DAYS`); when `0`, the TTL pass is
 * skipped (user-selected `Off`). Intentionally scheduled as a one-off from
 * [com.sza.fastmediasorter.core.init.AppStartupInitializer] rather than periodic —
 * once per launch is enough for cleanup that is not time-critical.
 */
@HiltWorker
class StreamingCacheStartupGcWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val streamingCacheRepository: StreamingCacheRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val correlationId = UUID.randomUUID().toString().take(8)
        val ttlDays = inputData.getInt(KEY_TTL_DAYS, DEFAULT_TTL_DAYS)
        Timber.d("[stream-cache-gc/$correlationId] starting (ttlDays=%d)", ttlDays)

        return try {
            val pruned = streamingCacheRepository.verifyAndPrune()
            if (pruned > 0) {
                Timber.i("[stream-cache-gc/$correlationId] verifyAndPrune removed %d missing-file rows", pruned)
            }

            if (ttlDays > 0) {
                val ttlMs = TimeUnit.DAYS.toMillis(ttlDays.toLong())
                val now = System.currentTimeMillis()
                val stale = streamingCacheRepository.findStaleByTtl(now, ttlMs)
                var deleted = 0
                for (entry in stale) {
                    if (streamingCacheRepository.delete(entry.resourceHash)) deleted++
                }
                if (deleted > 0) {
                    Timber.i(
                        "[stream-cache-gc/$correlationId] TTL eviction removed %d entries older than %d days",
                        deleted, ttlDays
                    )
                }
            } else {
                Timber.d("[stream-cache-gc/$correlationId] TTL disabled — skipping eviction pass")
            }

            Timber.i("[stream-cache-gc/$correlationId] completed")
            Result.success()
        } catch (t: Throwable) {
            Timber.e(t, "[stream-cache-gc/$correlationId] failed")
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "streaming_cache_startup_gc"
        const val KEY_TTL_DAYS = "ttl_days"

        /** Fallback when settings have not been wired yet (matches spec default). */
        const val DEFAULT_TTL_DAYS = 7
    }
}
