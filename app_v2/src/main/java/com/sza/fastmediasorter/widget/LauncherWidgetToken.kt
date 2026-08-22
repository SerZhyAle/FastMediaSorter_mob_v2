package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.Context

/**
 * S1930: the instance id a launcher desktop cell uses where a home-screen widget would use its
 * `appWidgetId`.
 *
 * Tokens are negative because [AppWidgetManager] only ever hands out increasing positive ids and keeps
 * `INVALID_APPWIDGET_ID` at zero, so the two allocators cannot collide however either one grows - the
 * disjointness is a property of the ranges rather than an agreement between the callers.
 *
 * A widget's configuration chain - config screen, snapshot store, refresher, cleanup - takes a plain
 * `Int` and stays indifferent to which allocator minted it. Only the calls that hand the id back to the
 * platform care, and they ask [isLauncherToken] first.
 */
object LauncherWidgetToken {

    /** Reserved for "this cell has no configured instance yet"; never returned by [mint]. */
    const val NONE: Int = Int.MIN_VALUE

    /** Most positive value a token may take. Everything from here down to [NONE] exclusive is ours. */
    const val MAX_TOKEN: Int = -1

    private const val PREFS = "launcher_widget_tokens"
    private const val KEY_LAST = "last_minted"

    /**
     * Next unused token, persisted so a token survives the process that issued it - the launcher cell
     * outlives it and keeps pointing at the same snapshot.
     */
    fun mint(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getInt(KEY_LAST, 0) - 1
        val token = if (next <= NONE) MAX_TOKEN else next
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
