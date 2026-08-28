package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.Context

/**
 * S1930: the instance id a launcher desktop cell uses where a home-screen widget would use its
 * `appWidgetId`.
 *
 * Tokens are negative because [AppWidgetManager] only ever hands out increasing positive ids and keeps
 * `INVALID_APPWIDGET_ID` at zero, so the platform's allocator can never reach this range however it
 * grows.
 *
 * The platform is not the only other allocator, though, and that is why the range starts a million
 * down rather than at -1. The app itself already parks hand-written sentinels in the shallow negatives
 * - `CameraQuickCaptureLaunchManager.PANEL_APP_WIDGET_ID` is -1000, and it travels in the same
 * `EXTRA_APPWIDGET_ID` extra - so a range counting down from -1 would have handed its thousandth cell
 * that exact value, and every capture from that cell would have gone to the panel's camera folder
 * instead of the configured target, silently. A sentinel somebody types by hand is a small round
 * number; [MAX_TOKEN] sits below every one of them, and [LauncherWidgetTokenTest] pins the ones that
 * exist today.
 *
 * A widget's configuration chain - config screen, snapshot store, refresher, cleanup - takes a plain
 * `Int` and stays indifferent to which allocator minted it. Only the calls that hand the id back to the
 * platform care, and they ask [isLauncherToken] first.
 */
object LauncherWidgetToken {

    /** Reserved for "this cell has no configured instance yet"; never returned by [mint]. */
    const val NONE: Int = Int.MIN_VALUE

    /**
     * Most positive value a token may take. Everything from here down to [NONE] exclusive is ours; the
     * million above it is left to the app's own hand-written sentinels (see the class KDoc).
     */
    const val MAX_TOKEN: Int = -1_000_000

    private const val PREFS = "launcher_widget_tokens"
    private const val KEY_LAST = "last_minted"

    /**
     * Next unused token, persisted so a token survives the process that issued it - the launcher cell
     * outlives it and keeps pointing at the same snapshot.
     */
    fun mint(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_LAST, MAX_TOKEN + 1)
        // Wrapping before the subtraction, not after: `NONE - 1` overflows to Int.MAX_VALUE, which is a
        // real appWidgetId and would make isLauncherToken false for a token this object just issued.
        val token = if (last <= NONE + 1 || last > MAX_TOKEN + 1) MAX_TOKEN else last - 1
        prefs.edit().putInt(KEY_LAST, token).apply()
        return token
    }

    /**
     * True when [id] was minted here rather than by the platform.
     *
     * Written as two comparisons on purpose: `id in MAX_TOKEN downTo NONE + 1` reads the same but builds
     * an `IntProgression`, and `in` on a progression is a linear scan, not a range test.
     */
    fun isLauncherToken(id: Int): Boolean = id <= MAX_TOKEN && id > NONE
}
