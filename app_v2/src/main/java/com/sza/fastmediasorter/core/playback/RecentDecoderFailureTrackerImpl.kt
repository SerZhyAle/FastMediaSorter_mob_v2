package com.sza.fastmediasorter.core.playback

import android.os.SystemClock
import com.sza.fastmediasorter.core.playback.RecentDecoderFailureTracker.Companion.DECODER_COOLDOWN_MS
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Process-scoped in-memory implementation of [RecentDecoderFailureTracker] for S0213 Pillar A.
 *
 * Backed by a [ConcurrentHashMap] of `path -> elapsedRealtime` marks. Entries are evicted lazily
 * on the first read after [DECODER_COOLDOWN_MS] elapses, plus aggressively via [clearAll] when a
 * different source plays a frame successfully.
 */
@Singleton
class RecentDecoderFailureTrackerImpl @Inject constructor() : RecentDecoderFailureTracker {

    private val marks = ConcurrentHashMap<String, Long>()

    override fun markFailed(path: String) {
        marks[path] = SystemClock.elapsedRealtime()
        Timber.d("RecentDecoderFailureTracker: marked path=$path")
    }

    override fun isInCooldown(path: String): Boolean {
        val markedAt = marks[path] ?: return false
        val elapsed = SystemClock.elapsedRealtime() - markedAt
        if (elapsed >= DECODER_COOLDOWN_MS) {
            marks.remove(path)
            return false
        }
        return true
    }

    override fun cooldownRemainingMs(path: String): Long {
        val markedAt = marks[path] ?: return 0L
        return (markedAt + DECODER_COOLDOWN_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    override fun clearAll() {
        if (marks.isEmpty()) return
        val cleared = marks.size
        marks.clear()
        Timber.d("RecentDecoderFailureTracker: cleared $cleared entries")
    }
}
