package com.sza.fastmediasorter.wear.domain.model

/**
 * What the watch draws behind its screens (S2000).
 *
 * Exactly three values, because the owner named three. A "none" value is deliberately absent: black is
 * what shows before a background is ready, not something the user picks.
 */
enum class WearBackgroundMode {
    BRANDED_ANIMATION,
    BRANDED_STILL,
    IMAGE;

    companion object {
        /**
         * An unknown name means a phone sent a value this watch build does not have, and the watch
         * must still draw something - so it draws the one background that needs no delivered file.
         */
        fun fromNameOrDefault(name: String?): WearBackgroundMode =
            entries.firstOrNull { it.name == name } ?: BRANDED_ANIMATION
    }
}
