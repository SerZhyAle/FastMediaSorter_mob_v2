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
    /**
     * S0593: a real user-initiated play that reached playback - green, and counted as a play.
     *
     * S1509: this used to be `invoke(id, ok)` whose false branch wrote [OUTCOME_FAIL] unconditionally.
     * Splitting the two directions apart is deliberate: a failure needs to know whether the device had
     * a network before it can be charged to the channel, and a single boolean gave the caller no way to
     * say so. Failures go through [recordPlayFailure].
     */
    suspend fun recordPlaySuccess(id: String) {
        repository.recordPlayOutcome(id, OUTCOME_OK)
        // Resolve the stored kind so the audio/video split is accurate; this is a play-start event, not
        // a hot path, so one lightweight lookup is fine.
        repository.markPlayed(id, System.currentTimeMillis())
        val kind = if (repository.getMediaKind(id) == MEDIA_KIND_AUDIO) ViewKind.AUDIO else ViewKind.VIDEO
        statsSink.record(StatsEvent.StreamPlayed(kind = kind))
    }

    /**
     * S0700: a reachability probe / grid frame capture. Reachable -> green; unreachable -> amber "unknown"
     * (NOT red). Red is reserved for a real play the user started that failed (see [invoke]). A probe never
     * counts as a play, so no usage statistic is recorded.
     */
    suspend fun recordProbe(id: String, reachable: Boolean) {
        repository.recordPlayOutcome(id, if (reachable) OUTCOME_OK else OUTCOME_UNKNOWN)
    }

    /**
     * S1509: a real play the user started that ended in failure. Red is recorded only when the device
     * actually had a network - during an outage every channel fails at once, so blaming the one the user
     * happened to open would redden a working list for a cause that was never any channel's.
     */
    suspend fun recordPlayFailure(id: String, hasNetwork: Boolean) {
        val outcome = if (isChannelAttributableFailure(hasNetwork)) OUTCOME_FAIL else OUTCOME_UNKNOWN
        repository.recordPlayOutcome(id, outcome)
    }

    companion object {
        const val OUTCOME_OK = "OK"
        const val OUTCOME_FAIL = "FAIL"
        // S0700: an inconclusive probe; the row/tile bullet renders it as the amber "not yet confirmed" state.
        const val OUTCOME_UNKNOWN = "UNKNOWN"

        /**
         * S1509: whether a playback failure says anything about the channel at all. This is the single home
         * of that rule - S1469's grid-capture cooldown delegates here instead of restating it, because the
         * two copies are what let the terminal dialog drift away from the probe path.
         */
        fun isChannelAttributableFailure(hasNetwork: Boolean): Boolean = hasNetwork
        // Mirrors StreamMediaKindClassifier's stored kind; AUDIO -> ViewKind.AUDIO, RTSP/VIDEO -> VIDEO.
        private const val MEDIA_KIND_AUDIO = "AUDIO"
    }
}
