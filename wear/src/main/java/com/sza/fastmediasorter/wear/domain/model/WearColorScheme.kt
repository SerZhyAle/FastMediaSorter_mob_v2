package com.sza.fastmediasorter.wear.domain.model

/**
 * The colour scheme the watch interface is drawn in (S2522).
 *
 * Eight members: a plain light and dark pair, and three accent families in both. The phone offers a
 * ninth, AUTO, which follows the device night mode - it is deliberately absent here, because Wear OS
 * gives the owner no system light/dark switch and reports night on every current device, so an AUTO
 * member could never produce a result different from [DARK] and would be a control that does nothing.
 *
 * [isLight] is carried on the member rather than derived where it is needed: the palette table and the
 * background layer both ask the same question, and two independent derivations of one fact drift.
 */
enum class WearColorScheme(val isLight: Boolean) {
    DARK(isLight = false),
    LIGHT(isLight = true),
    DARK_GREEN(isLight = false),
    DARK_BLUE(isLight = false),
    DARK_RED(isLight = false),
    LIGHT_GREEN(isLight = true),
    LIGHT_BLUE(isLight = true),
    LIGHT_RED(isLight = true);

    companion object {

        /** Reproduces the appearance the watch had before this setting existed. */
        val DEFAULT = DARK

        /**
         * An unknown name means the phone sent a value this watch build does not have, so the watch
         * still has to draw something. The phone's own AUTO arrives here and resolves to [DEFAULT],
         * which is what AUTO resolves to on a Wear OS device anyway.
         */
        fun fromNameOrDefault(name: String?): WearColorScheme =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
