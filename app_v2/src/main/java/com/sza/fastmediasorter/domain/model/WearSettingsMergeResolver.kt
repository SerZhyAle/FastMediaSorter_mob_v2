package com.sza.fastmediasorter.domain.model

/**
 * S2093: what to do with one incoming field value.
 *
 * @param apply true when the incoming value replaces the stored one.
 * @param stampEpochMillis the edit time to record for the applied value, in the receiver's own time
 *   base, or null when the sender carried no stamp for this field.
 */
data class WearSettingsMergeDecision(
    val apply: Boolean,
    val stampEpochMillis: Long?
)

/**
 * S2093: resolves an incoming settings set against the stored one, field by field, keeping the later
 * edit (ADR-1).
 *
 * The comparison is made in the receiver's time base. Two devices' clocks are never in step, and a
 * constant offset either way would otherwise decide every field regardless of what the owner actually
 * changed last. The envelope already carries `sentAt` and the receiver knows when it took delivery, so
 * the difference of those two numbers is that offset, measured by the exchange itself rather than
 * assumed - which is why the magnitude of the skew never has to be known.
 *
 * Written once per module on purpose: the two modules compile separately with no shared artifact, and
 * `scripts/quality/assert-wear-settings-parity.ps1` is what keeps the copies honest.
 *
 * A model rather than a use case: it holds the ranking policy and touches nothing outside its own
 * arguments, so Rule 6's `*UseCase` suffix would name it something it is not.
 */
class WearSettingsMergeResolver(
    private val incomingStamps: Map<String, Long>?,
    private val localStamps: Map<String, Long>,
    private val skewMillis: Long,
    private val rejectedFields: Set<String>
) {

    fun resolve(field: String): WearSettingsMergeDecision {
        if (field in rejectedFields) return KEEP
        val incoming = incomingStamps?.get(field)
        val local = localStamps[field]
        return when {
            // A sender that predates this ticket carries no stamps at all. Applying its values is
            // exactly the one-way behaviour that shipped before, so an older watch keeps working.
            incomingStamps == null -> APPLY_UNSTAMPED
            // The sender stamped some fields but not this one. A stored value that carries a stamp was
            // deliberately changed here and outranks a value whose age the sender could not state.
            incoming == null -> if (local == null) APPLY_UNSTAMPED else KEEP
            local == null -> WearSettingsMergeDecision(apply = true, stampEpochMillis = incoming + skewMillis)
            incoming + skewMillis > local ->
                WearSettingsMergeDecision(apply = true, stampEpochMillis = incoming + skewMillis)
            else -> KEEP
        }
    }

    private companion object {
        val KEEP = WearSettingsMergeDecision(apply = false, stampEpochMillis = null)
        val APPLY_UNSTAMPED = WearSettingsMergeDecision(apply = true, stampEpochMillis = null)
    }
}
