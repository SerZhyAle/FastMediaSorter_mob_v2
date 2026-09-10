package com.sza.fastmediasorter.core.debug

/**
 * S2380: contract between the host-side UI sweep and [ThemeTestReceiver].
 *
 * The sweep has to set each of the nine colour-theme values on a RUNNING debug process: the value
 * lives in a SharedPreferences file that Android keeps in memory from the first read, so editing it
 * from the host changes nothing, and killing the process to force a re-read destroys the walker's
 * navigation position - the one thing the sweep must not lose.
 *
 * Shaped after S1986's [CameraTestHooks] / [CameraTestOpenReceiver] pair, including the
 * acknowledgement code: `am broadcast` prints "Broadcast completed: result=0" whether a receiver ran
 * or not, so without a distinctive result code the sweep cannot tell "theme applied" from "this build
 * has no hook" and would label a frame with a theme that was never set.
 *
 * Debug builds only - this file lives in `src/debug`, so a release build carries neither it nor the
 * receiver.
 *
 * Usage:
 *   adb shell am broadcast -a com.sza.fastmediasorter.debug.THEME_TEST_SET --es theme DARK_GREEN \
 *     -p com.sza.fastmediasorter.debug
 */
object ThemeTestHooks {

    const val ACTION_SET_THEME = "com.sza.fastmediasorter.debug.THEME_TEST_SET"

    /** String extra carrying one of [VALUES]; matched case-insensitively after trimming. */
    const val EXTRA_THEME = "theme"

    /** The value was recognised, stored and applied to the running process. */
    const val ACK_APPLIED = 2380

    /**
     * The value was not one of [VALUES] and nothing was changed.
     *
     * Distinct from [ACK_APPLIED] on purpose: `ColorThemePrefs.normalizeValue` silently folds an
     * unknown value to "AUTO", so a sweep that mistypes a cell would otherwise photograph the default
     * theme and file it under the name it asked for.
     */
    const val ACK_REJECTED = 2381

    /**
     * The nine accepted values, in `ColorThemePrefs`'s own raw spelling: the three base modes plus the
     * six S0569 accent variants.
     */
    val VALUES: Set<String> = setOf(
        "AUTO",
        "LIGHT",
        "DARK",
        "DARK_GREEN",
        "DARK_BLUE",
        "DARK_RED",
        "LIGHT_GREEN",
        "LIGHT_BLUE",
        "LIGHT_RED",
    )
}
