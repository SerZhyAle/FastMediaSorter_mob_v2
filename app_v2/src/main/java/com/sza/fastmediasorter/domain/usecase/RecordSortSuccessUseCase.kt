package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.preferences.ReviewEligibilityDataStore
import timber.log.Timber
import javax.inject.Inject

/**
 * Records one or more successful sort operations (Move or Copy) and returns `true`
 * when the user has reached the eligibility threshold and the review cooldown has expired.
 *
 * Threshold constants are tunable without domain-layer changes (S0135).
 * Caller must immediately trigger the review flow on `true` and call
 * [ReviewEligibilityDataStore.markReviewShown] to reset the trigger.
 */
class RecordSortSuccessUseCase @Inject constructor(
    private val store: ReviewEligibilityDataStore
) {

    companion object {
        /** Minimum cumulative Move+Copy operations before first eligibility check. */
        const val OPS_THRESHOLD: Long = 20L

        /** Minimum number of sessions that included at least one sort operation. */
        const val SESSIONS_THRESHOLD: Long = 3L

        /** Minimum gap between consecutive review requests (90 days). */
        const val COOLDOWN_MS: Long = 90L * 24L * 60L * 60L * 1_000L
    }

    /**
     * Records [count] successful sort operations and evaluates eligibility.
     *
     * @param count  Number of files successfully moved or copied in this operation.
     * @return `true` if the review dialog should be triggered; `false` otherwise.
     *
     * Session tracking: the first call per process lifetime increments [ReviewEligibilityDataStore.SESSIONS_WITH_SORT].
     * Subsequent calls in the same process skip the session increment (one increment per launch).
     */
    suspend fun record(count: Int = 1): Boolean {
        val newOps = store.incrementSortOps(count.toLong())
        Timber.d("RecordSortSuccessUseCase: ops=$newOps count=$count")

        val snap = store.snapshot()
        val cooldownExpired = snap.reviewShownEpoch == 0L ||
            (System.currentTimeMillis() - snap.reviewShownEpoch) >= COOLDOWN_MS

        val eligible = snap.sortOpsCount >= OPS_THRESHOLD &&
            snap.sessionsWithSort >= SESSIONS_THRESHOLD &&
            cooldownExpired

        Timber.d(
            "RecordSortSuccessUseCase: eligible=$eligible " +
            "ops=${snap.sortOpsCount}/$OPS_THRESHOLD " +
            "sessions=${snap.sessionsWithSort}/$SESSIONS_THRESHOLD " +
            "cooldownExpired=$cooldownExpired"
        )

        return eligible
    }

    /**
     * Increments the session counter. Call once per app launch that includes a sort activity.
     * Safe to call multiple times - [ReviewEligibilityDataStore.incrementSessions] is idempotent
     * relative to session semantics only when the caller gates it per process lifetime.
     */
    suspend fun recordSession() {
        val sessions = store.incrementSessions()
        Timber.d("RecordSortSuccessUseCase: session recorded sessions=$sessions")
    }

    /**
     * Delegator - records that the review dialog was shown. Used by `ReviewRequestManager`
     * to reset the cooldown without holding a direct reference to the data store.
     */
    suspend fun markReviewShown() = store.markReviewShown()
}
