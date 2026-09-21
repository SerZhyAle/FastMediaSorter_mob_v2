package com.sza.fastmediasorter.wear.domain.model

/**
 * What the watch draws behind its screens (S2000).
 *
 * [NONE] is black, and it is a choice like the other three rather than only the state before a
 * background is ready: S3362 made it the starting value of the flavor Play distributes, where Wear OS
 * review item WO-V13 asks for a black background.
 *
 * Which value a watch starts on is NOT decided here - it is `WearAppearanceDefaults`, answered per
 * flavor. This enum is the vocabulary; the starting point is a build-variant question.
 */
enum class WearBackgroundMode {
    NONE,
    BRANDED_ANIMATION,
    BRANDED_STILL,
    IMAGE;

    companion object {
        /**
         * An unknown name means a phone sent a value this watch build does not have, and the watch
         * must still draw something - so it draws the one background that needs no delivered file.
         *
         * For a name read back from this watch's OWN store, `WearAppearancePreferencesImpl` resolves
         * the flavor's starting value instead: there an unreadable name means a downgrade past a mode
         * rather than a phone speaking a wider vocabulary, and the two recoveries differ (S3362).
         */
        fun fromNameOrDefault(name: String?): WearBackgroundMode =
            entries.firstOrNull { it.name == name } ?: BRANDED_ANIMATION
    }
}
