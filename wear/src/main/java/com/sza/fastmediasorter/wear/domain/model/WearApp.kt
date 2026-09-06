package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.StringRes

/**
 * Mini-programs of the watch, in the order the owner fixed for the first set.
 *
 * [canonicalKey] is the phone's route key for the same program, not a name invented here. The phone's
 * program registry addresses a program by that key, so keeping it identical is what lets the watch list
 * be absorbed into that registry later without renaming a key already saved on a device.
 */
enum class WearAppId(val canonicalKey: String) {
    CALCULATOR("calculator"),
    NETWORK_MONITOR("network_monitor"),
    GAME("game"),
    VOICE_RECORDER("voice_recorder"),

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
