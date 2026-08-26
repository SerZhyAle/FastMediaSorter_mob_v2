package com.sza.fastmediasorter.wear.domain.model

/**
 * What the watch draws behind its screens (S2000).
 *
 * Exactly two values, because the owner named two. A "none" value is deliberately absent: black is
 * what shows before a background is ready, not something the user picks.
 */
enum class WearBackgroundMode {
    BRANDED_ANIMATION,
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
