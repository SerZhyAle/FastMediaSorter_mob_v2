package com.sza.fastmediasorter.domain.model

/**
 * S2716: which measurement system the user reads values in.
 *
 * Named after the system rather than after the temperature on purpose: today the only consumer is the
 * desktop weather pair, and a future quantity is meant to become a second consumer of this value
 * instead of a second settings row.
 */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
    ;

    companion object {

        val DEFAULT: UnitSystem = METRIC

        /**
         * A stored value this build does not know resolves to the default rather than throwing, so a
         * settings file written by a newer build keeps working after a downgrade.
         */
        fun fromNameOrDefault(name: String?): UnitSystem =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
