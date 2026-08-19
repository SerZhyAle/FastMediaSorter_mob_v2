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
    GAME("game")
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
