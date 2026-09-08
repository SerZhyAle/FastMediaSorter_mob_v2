package com.sza.fastmediasorter.wear.ui.apps

import androidx.annotation.ColorRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearAppId

/**
 * The single place on the watch that knows what colour a mini-program is.
 *
 * S2510: the phone half is app_v2's core/panel/SubProgramAccentCatalog. The two modules share no
 * source, so the entire contract between them is the resource NAME - each declares the same eight
 * `color_program_accent_*` keys and tunes its own values, because the watch screen is dark and may
 * carry a user photo under a scrim, where a tone picked for a light phone surface either glares or
 * disappears.
 *
 * Keyed on [WearAppId] rather than on its `canonicalKey` deliberately: the watch owns which programs
 * it shows, and one canonicalKey is known to diverge from the phone's route key (S2579), which must
 * not be able to reach the colour.
 *
 * Exhaustive with no else branch on purpose: a sixth watch program must fail compilation here rather
 * than silently inherit a default colour.
 */
object WearAppAccentCatalog {

    @ColorRes
    fun accentFor(id: WearAppId): Int = when (id) {
        WearAppId.CALCULATOR -> R.color.color_program_accent_orange
        WearAppId.NETWORK_MONITOR -> R.color.color_program_accent_teal
        WearAppId.GAME -> R.color.color_program_accent_green
        WearAppId.VOICE_RECORDER -> R.color.color_program_accent_red
        // Kept out of the phone's compute family, which already holds the calculator: these five are
        // the whole list on a watch, so two of them sharing a tone would undo the point of colouring.
        WearAppId.SYSTEM_INFO -> R.color.color_program_accent_indigo
        // S2516: amber, the tone the phone gives the whole flashlight family - the one program of this
        // list that exists on both devices, so it is also the one whose colour must not diverge.
        WearAppId.WATER_FLASHLIGHT -> R.color.color_program_accent_amber
        // S2458: blue, unused by the list so far. Deliberately not teal - the Network Monitor holds it,
        // and the two monitors sit next to each other in a list read at a glance on a small screen.
        WearAppId.MOTION_MONITOR -> R.color.color_program_accent_blue
        // S2457: purple, the last of the eight names this palette declares and the only one no program
        // had taken - so the seventh program needed no new tone, and the phone's half stays name-for-name.
        WearAppId.BODY_SENSOR -> R.color.color_program_accent_purple
        // S2509: cyan, the ninth name and the first this palette does not share with the phone. A
        // family tone shared with the Voice Recorder was the first attempt and the accent test
        // refused it: the whole list fits one watch screen, so a repeat is two visible rows looking
        // alike rather than a harmless collision between distant ones.
        WearAppId.BROADCAST -> R.color.color_program_accent_cyan
    }
}
