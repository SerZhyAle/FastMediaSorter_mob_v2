package com.sza.fastmediasorter.domain.model

/**
 * S2484: the transferable halves of one watch exchange, named so a combined outcome can say which of
 * them moved.
 *
 * Four values rather than one flag because the legs fail independently: an unreachable watch stops
 * every one of them, an empty resource selection stops a single leg, and a rejected settings payload
 * stops another after the resources have already landed.
 *
 * Twin on the watch side: `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSyncOutcome.kt`.
 * The two modules cannot share a class, so the copy is deliberate and the pair must move together.
 */
enum class WearSyncLeg {
    RESOURCES_OUT,
    SETTINGS_OUT,
    RESOURCES_IN,
    SETTINGS_IN
}

/**
 * S2484: how one leg of an exchange ended.
 *
 * [NothingToSend] is deliberately not a failure. S1781 established that an empty watch-resource
 * selection means the owner marked nothing for the watch, which is a valid state; reporting it as an
 * error would warn about a choice the owner made on purpose.
 */
sealed class WearSyncLegResult {
    data class Succeeded(val itemCount: Int) : WearSyncLegResult()
    data object NothingToSend : WearSyncLegResult()
    data class Failed(val reason: String) : WearSyncLegResult()
}

/**
 * S2484: the result of one unified exchange, leg by leg.
 *
 * Held as a map rather than a single verdict because strategic 5.3 requires partial success to be
 * expressible - "the settings arrived, the resources did not" is a real outcome that one boolean
 * would have to round to whichever side it favoured.
 */
data class WearSyncOutcome(
    val legs: Map<WearSyncLeg, WearSyncLegResult>
) {

    val failedLegs: List<WearSyncLeg>
        get() = legs.filterValues { it is WearSyncLegResult.Failed }.keys.toList()

    val allSucceeded: Boolean
        get() = legs.isNotEmpty() && failedLegs.isEmpty()

    /** Merges a leg reported by a listener into an outcome the orchestrator already produced. */
    fun withLeg(leg: WearSyncLeg, result: WearSyncLegResult): WearSyncOutcome =
        copy(legs = legs + (leg to result))

    companion object {

        /** Every leg refused for one shared reason - the shape an unreachable watch produces. */
        fun allFailed(reason: String): WearSyncOutcome = WearSyncOutcome(
            WearSyncLeg.entries.associateWith { WearSyncLegResult.Failed(reason) }
        )
    }
}
