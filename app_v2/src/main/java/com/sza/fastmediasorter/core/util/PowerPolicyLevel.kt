package com.sza.fastmediasorter.core.util

/**
 * S2536: how strongly the app is currently conserving power.
 *
 * [REDUCED] is the user's own "disable animations" switch. [SAVING] is stronger and is entered
 * automatically at a low charge or under the system power saver, so it also stops motion that
 * carries meaning but costs a continuous full-screen redraw, and it stands down the global
 * keep-screen-on.
 */
enum class PowerPolicyLevel {
    NORMAL,
    REDUCED,
    SAVING
}
