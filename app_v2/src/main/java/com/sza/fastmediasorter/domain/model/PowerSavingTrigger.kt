package com.sza.fastmediasorter.domain.model

private const val THRESHOLD_LOWEST = 10
private const val THRESHOLD_SYSTEM_MATCH = 15
private const val THRESHOLD_DEFAULT = 20
private const val THRESHOLD_EARLY = 30

/**
 * S2536: when the phone enters the power-saving level on its own.
 *
 * The default is 20 percent so the app quietens before Android's own battery saver engages at 15 -
 * entering at the same moment would make this app's effect indistinguishable from the system's.
 *
 * One control covers both halves of the question the owner asked for: [ALWAYS] is the manual switch
 * and the [BELOW_10]..[BELOW_30] entries are the automatic one, so no second settings row appears.
 *
 * Mirrored on the watch as `com.sza.fastmediasorter.wear.domain.model.PowerSavingTrigger`. Only this
 * VALUE crosses the settings channel - each device judges its own charge, because a phone at eighty
 * percent says nothing about a watch at twelve.
 */
enum class PowerSavingTrigger(val thresholdPercent: Int?) {
    /** Never automatic. The mode is still reachable through the system power saver. */
    OFF(null),

    /** Always on, whatever the charge. */
    ALWAYS(null),

    BELOW_10(THRESHOLD_LOWEST),
    BELOW_15(THRESHOLD_SYSTEM_MATCH),
    BELOW_20(THRESHOLD_DEFAULT),
    BELOW_30(THRESHOLD_EARLY);

    companion object {

        val DEFAULT: PowerSavingTrigger = BELOW_20

        /**
         * A stored value this build does not know resolves to the default rather than throwing, so a
         * settings file written by a newer build keeps working after a downgrade.
         */
        fun fromNameOrDefault(name: String?): PowerSavingTrigger =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
