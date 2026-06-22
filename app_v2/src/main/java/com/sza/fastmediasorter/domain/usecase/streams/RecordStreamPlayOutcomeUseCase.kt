package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
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
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(id: String, ok: Boolean) =
        repository.recordPlayOutcome(id, if (ok) OUTCOME_OK else OUTCOME_FAIL)

    companion object {
        const val OUTCOME_OK = "OK"
        const val OUTCOME_FAIL = "FAIL"
    }
}
