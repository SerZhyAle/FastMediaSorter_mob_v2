package com.sza.fastmediasorter.wear.domain.model

/**
 * S2731: watch mirror of the phone's `UnitSystem` - which measurement system the user reads values in.
 *
 * No watch surface reads this yet; the value is inherited from the phone and stored so a future
 * consumer needs no new sync plumbing.
 */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
    ;

    companion object {

        val DEFAULT: UnitSystem = METRIC

        /**
         * A stored value this build does not know resolves to the default rather than throwing:
         * settings arrive over the wire from a phone that may be newer than this watch.
         */
        fun fromNameOrDefault(name: String?): UnitSystem =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
