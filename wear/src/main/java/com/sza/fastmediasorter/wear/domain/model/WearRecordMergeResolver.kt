package com.sza.fastmediasorter.wear.domain.model

/**
 * S2502: what to do with one incoming record.
 *
 * @param apply true when the incoming record replaces the stored one.
 * @param stampEpochMillis the edit time to record for the applied record, in the receiver's own time
 *   base, or null when the sender carried no stamp for it.
 */
data class WearRecordMergeDecision(
    val apply: Boolean,
    val stampEpochMillis: Long?
)

/**
 * S2502: ranks one incoming record against the stored one, keeping the later edit.
 *
 * A record is judged whole, unlike a settings field: a resource is a connection configuration whose
 * address, credentials, share and path are only meaningful together, so merging them separately would
 * assemble a record neither side ever entered.
 *
 * The comparison is made in the receiver's time base. Two devices' clocks are never in step, and a
 * constant offset either way would otherwise decide every record regardless of what the owner actually
 * changed last. The exchange already carries the time it was sent and the receiver knows when it took
 * delivery, so the difference of those two numbers is that offset, measured rather than assumed -
 * which is why the magnitude of the skew never has to be known.
 *
 * [senderCarriesStamps] is a statement about the whole batch, not about one record: a sender that
 * predates this ticket stamps nothing at all, and applying its records is exactly the behaviour that
 * shipped before, so an older phone or watch keeps working.
 *
 * Written once per module on purpose: the two modules compile separately with no shared artifact, and
 * `scripts/quality/assert-wear-record-merge-parity.ps1` is what keeps the copies honest.
 *
 * A model rather than a use case: it holds the ranking policy and touches nothing outside its own
 * arguments, so Rule 6's `*UseCase` suffix would name it something it is not.
 */
class WearRecordMergeResolver(
    private val senderCarriesStamps: Boolean,
    private val skewMillis: Long
) {

    fun resolve(incomingStamp: Long?, localStamp: Long?): WearRecordMergeDecision {
        val corrected = if (incomingStamp == null) null else incomingStamp + skewMillis
        return when {
            !senderCarriesStamps -> APPLY_UNSTAMPED
            // The sender stamped some records but not this one. A stored record that carries a stamp
            // was deliberately changed here and outranks a record whose age the sender could not state.
            corrected == null -> if (localStamp == null) APPLY_UNSTAMPED else KEEP
            localStamp == null -> WearRecordMergeDecision(apply = true, stampEpochMillis = corrected)
            corrected > localStamp -> WearRecordMergeDecision(apply = true, stampEpochMillis = corrected)
            else -> KEEP
        }
    }

    private companion object {
        val KEEP = WearRecordMergeDecision(apply = false, stampEpochMillis = null)
        val APPLY_UNSTAMPED = WearRecordMergeDecision(apply = true, stampEpochMillis = null)
    }
}
