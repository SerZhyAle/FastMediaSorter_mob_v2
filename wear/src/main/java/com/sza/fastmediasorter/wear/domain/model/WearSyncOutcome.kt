package com.sza.fastmediasorter.wear.domain.model

/**
 * S2484: the transferable halves of one watch exchange, named so a combined outcome can say which of
 * them moved.
 *
 * Twin of `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSyncOutcome.kt`. The two
 * modules cannot share a class, so the copy is deliberate - the same shape `WearSettingsMergeResolver`
 * already uses on both sides - and the pair must be changed together.
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
 * [NothingToSend] is deliberately not a failure: a watch that holds no sources of its own has nothing
 * to send, which is a valid state rather than a fault to warn about.
 */
sealed class WearSyncLegResult {
    data class Succeeded(val itemCount: Int) : WearSyncLegResult()
    data object NothingToSend : WearSyncLegResult()
    data class Failed(val reason: String) : WearSyncLegResult()
}

/**
 * S2484: the result of one unified exchange, leg by leg.
 *
 * Held as a map rather than a single verdict because the legs are independent, so "the settings
 * arrived, the resources did not" is a real outcome one boolean could not carry.
 */
data class WearSyncOutcome(
    val legs: Map<WearSyncLeg, WearSyncLegResult>
) {

    val failedLegs: List<WearSyncLeg>
        get() = legs.filterValues { it is WearSyncLegResult.Failed }.keys.toList()

    val allSucceeded: Boolean
        get() = legs.isNotEmpty() && failedLegs.isEmpty()

    fun withLeg(leg: WearSyncLeg, result: WearSyncLegResult): WearSyncOutcome =
        copy(legs = legs + (leg to result))

    companion object {

        /** Every leg refused for one shared reason - the shape an unreachable phone produces. */
        fun allFailed(reason: String): WearSyncOutcome = WearSyncOutcome(
            WearSyncLeg.entries.associateWith { WearSyncLegResult.Failed(reason) }
        )
    }
}
