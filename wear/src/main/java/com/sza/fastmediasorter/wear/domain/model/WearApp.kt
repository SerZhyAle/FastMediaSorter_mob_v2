package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.StringRes

/**
 * Mini-programs of the watch, in the order the owner fixed for the first set.
 *
 * [canonicalKey] is the phone's route key for the same program, not a name invented here. The phone's
 * program registry addresses a program by that key, so keeping it identical is what lets the watch list
 * be absorbed into that registry later without renaming a key already saved on a device.
 *
 * S2579: the rule is two-part, because a program of this list may have no counterpart at all. A key is
 * either one of `InternalRouteCatalog`'s route keys, spelled identically, or the program lives only on
 * the watch and is named in `scripts/quality/wear-canonical-key-watch-only-baseline.txt` with its reason.
 * The single-part wording above held on the first five entries and quietly stopped being true when the
 * sensor programs joined; the gate `assert-wear-canonical-key-parity.ps1` now judges both halves.
 */
enum class WearAppId(val canonicalKey: String) {
    CALCULATOR("calculator"),
    NETWORK_MONITOR("network_monitor"),
    GAME("game"),

    /**
     * S2579: the watch's dictaphone, which the phone addresses as `quick_voice`. The watch yielded rather
     * than the phone: the phone's key is saved in launcher cells and app-launch panel layouts, so renaming
     * it would devalue a layout the owner built, while this key is saved nowhere at all.
     */
    VOICE_RECORDER("quick_voice"),

    /**
     * S2008: the watch's own report, moved here from Settings - it configures nothing, so it belongs
     * with the programs rather than with the switches.
     */
    SYSTEM_INFO("system_info"),

    /**
     * S2516: the display used as a light behind a lock no touch opens. Shares its `canonicalKey` with
     * the phone's program of the same name, which has a torch as well - the watch has no flash unit,
     * so only the lit screen half exists here.
     */
    WATER_FLASHLIGHT("water_flashlight"),

    /**
     * S2458: live motion and activity readings, for as long as its screen is open. Appended rather than
     * placed beside the Network Monitor it resembles: the order of this list is the owner's, and moving
     * an existing program is a decision this ticket was not asked to make.
     */
    MOTION_MONITOR("motion_monitor"),

    /**
     * S2457: one foreground heart-rate reading, with the reason printed when there cannot be one. The only
     * program of this list whose row is withheld in `standard` - Play reviews the heart-rate permissions
     * against six admitted use cases and a media sorter matches none (ADR-1), so the catalog answers
     * `isAvailable` from the build rather than listing it everywhere and explaining the absence inside.
     */
    BODY_SENSOR("body_sensor")
}

/**
 * One row of the Apps section.
 *
 * [isAvailable] exists from the first entry although none of the first three ever clears it: a program
 * that later needs a switch or a hardware check sets this field instead of forcing the list to grow a
 * second concept.
 */
data class WearApp(
    val id: WearAppId,
    @StringRes val labelRes: Int,
    val route: String,
    val isAvailable: Boolean = true
)
