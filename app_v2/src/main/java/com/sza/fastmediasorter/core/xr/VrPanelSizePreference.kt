package com.sza.fastmediasorter.core.xr

import android.content.Context
import androidx.core.content.edit
import timber.log.Timber

/**
 * Persists and restores the Quest panel window size across app launches.
 *
 * Uses SharedPreferences (not Room) so the size can be read BEFORE super.onCreate()
 * in MainActivity — Hilt/Room are not yet initialised at that point.
 *
 * Only active on XR headsets; safe to call on all flavors (values are never written on phones).
 */
object VrPanelSizePreference {

    private const val PREFS = "vr_panel_size"
    private const val KEY_W = "panel_w_px"
    private const val KEY_H = "panel_h_px"

    /** Default panel size on first launch — large landscape "cinema" preset for Quest 3. */
    const val DEFAULT_W = 1920
    const val DEFAULT_H = 1080

    // Minimum acceptable size: guards against Quest delivering a transient tiny-window event
    // (e.g., during the multi-window snap animation) which would overwrite the user's real size.
    private const val MIN_W = 800
    private const val MIN_H = 400

    /**
     * Saves the current window size.
     * Silently ignored if either dimension is below [MIN_W]/[MIN_H] to avoid capturing
     * transient minimised or system-snap states.
     */
    fun save(context: Context, widthPx: Int, heightPx: Int) {
        if (widthPx < MIN_W || heightPx < MIN_H) {
            Timber.d("VrPanelSizePreference: skip save — size ${widthPx}x${heightPx} below MIN threshold")
            return
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_W, widthPx)
            putInt(KEY_H, heightPx)
        }
        Timber.d("VrPanelSizePreference: saved ${widthPx}x${heightPx}")
    }

    /**
     * Returns the last saved size, or [DEFAULT_W]×[DEFAULT_H] if nothing was saved yet.
     */
    fun load(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val w = prefs.getInt(KEY_W, DEFAULT_W)
        val h = prefs.getInt(KEY_H, DEFAULT_H)
        return w to h
    }

    /** Returns true if the user has previously saved a custom size (i.e., not first launch). */
    fun hasUserSize(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_W)
}
