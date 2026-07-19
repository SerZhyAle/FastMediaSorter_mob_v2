package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.stats.ViewKind
import javax.inject.Inject

/**
 * S0593: record the outcome of the last local playback attempt for a stream source, driving the
 * green/red/amber status bullet in the Streams list. This is ground truth from the user's own device
 * - a real play succeeded or failed here - independent of any remote liveness probe.
 *
 * Keyed by the source id (the caller has already resolved the row). The outcome string is the single
 * source of truth shared with the adapter via [OUTCOME_OK] / [OUTCOME_FAIL].
 */
class RecordStreamPlayOutcomeUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
    private val statsSink: StatsSink,
) {
    /** S0593: a real user-initiated play. OK -> green + counted as a play; FAIL -> red (the user saw it fail). */
    suspend operator fun invoke(id: String, ok: Boolean) {
        repository.recordPlayOutcome(id, if (ok) OUTCOME_OK else OUTCOME_FAIL)
        // Count only a successful local play. Resolve the stored kind so the audio/video split is
        // accurate; this is a play-start event, not a hot path, so one lightweight lookup is fine.
        if (ok) {
            repository.markPlayed(id, System.currentTimeMillis())
            val kind = if (repository.getMediaKind(id) == MEDIA_KIND_AUDIO) ViewKind.AUDIO else ViewKind.VIDEO
            statsSink.record(StatsEvent.StreamPlayed(kind = kind))
        }
    }

    /**
     * S0700: a reachability probe / grid frame capture. Reachable -> green; unreachable -> amber "unknown"
     * (NOT red). Red is reserved for a real play the user started that failed (see [invoke]). A probe never
     * counts as a play, so no usage statistic is recorded.
     */
    suspend fun recordProbe(id: String, reachable: Boolean) {
        repository.recordPlayOutcome(id, if (reachable) OUTCOME_OK else OUTCOME_UNKNOWN)
    }

    companion object {
        const val OUTCOME_OK = "OK"
        const val OUTCOME_FAIL = "FAIL"
        // S0700: an inconclusive probe; the row/tile bullet renders it as the amber "not yet confirmed" state.
        const val OUTCOME_UNKNOWN = "UNKNOWN"
        // Mirrors StreamMediaKindClassifier's stored kind; AUDIO -> ViewKind.AUDIO, RTSP/VIDEO -> VIDEO.
        private const val MEDIA_KIND_AUDIO = "AUDIO"
    }
}
