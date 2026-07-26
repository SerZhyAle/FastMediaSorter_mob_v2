package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper

/**
 * Synchronous mirror of [AppSettings.useCompactElements] used to pick the player controls
 * layout (compact vs large) before the player Activity inflates its content view.
 *
 * Lives in a dedicated SharedPreferences file because the canonical setting is in DataStore
 * (async) and DefaultTimeBar reads bar/touch/scrubber sizes from XML at inflate time only -
 * meaning the choice must be made before super.onCreate. The setting toggle in
 * Settings → General writes here in addition to DataStore right before triggering an app restart.
 */
object PlayerLayoutModePrefs {
    private const val FILE_NAME = "player_layout_mode"
    private const val KEY_USE_COMPACT_ELEMENTS = "use_compact_elements"
    private const val KEY_BIG_BUTTONS_MODE = "big_buttons_mode"

    fun isCompact(context: Context): Boolean {
        // S1153: accepted narrow StrictMode exception. This mirror read must be synchronous - it
        // gates a theme overlay applied before super.onCreate/setContentView (DefaultTimeBar + dialog
        // styles read their size attrs only once at inflate), so it cannot be deferred off the main
        // thread the way the ticket's other startup reads were. Same treatment as the return-to-settings
        // read (S1153 section 3). allowDiskIO is a pass-through in release (no StrictMode there).
        return StrictModeHelper.allowDiskIO {
            val prefs = context.applicationContext
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            // Default mirrors AppSettings.useCompactElements default (= false, i.e. large/expanded).
            prefs.getBoolean(KEY_USE_COMPACT_ELEMENTS, false)
        }
    }

    fun setCompact(context: Context, useCompact: Boolean) {
        context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_COMPACT_ELEMENTS, useCompact)
            .apply()
    }

    fun isBigButtonsMode(context: Context): Boolean {
        // S1153: same accepted synchronous mirror read as isCompact - read once at player init before
        // inflate, so wrap in allowDiskIO rather than defer. Fires on resume-to-player cold starts (S1152).
        return StrictModeHelper.allowDiskIO {
            val prefs = context.applicationContext
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            // Default false - standard layout unless user enables Big Buttons Mode (ADR-2: read once at player init).
            prefs.getBoolean(KEY_BIG_BUTTONS_MODE, false)
        }
    }

    fun setBigButtonsMode(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BIG_BUTTONS_MODE, enabled)
            .apply()
    }

    /**
     * Apply the LargePlayerControls theme overlay when compact is OFF.
     * Must be called before super.onCreate (i.e. before setContentView), because
     * DefaultTimeBar reads its bar/touch/scrubber XML attrs only once at inflate.
     */
    fun applyControlsThemeOverlay(activity: Activity) {
        if (!isCompact(activity)) {
            activity.theme.applyStyle(R.style.Theme_FastMediaSorter_LargePlayerControls, true)
        }
    }

    /**
     * Apply the compact dialog-action-button overlay when compact mode is ON, so dialog confirm/cancel
     * buttons shrink to 50% along with the rest of the UI. Applied app-wide from BaseActivity.onCreate;
     * uses the synchronous prefs mirror because the dialog styles read the size attr at inflate time.
     */
    fun applyCompactDialogButtonsOverlay(activity: Activity) {
        if (isCompact(activity)) {
            activity.theme.applyStyle(R.style.ThemeOverlay_FastMediaSorter_CompactDialogButtons, true)
        }
    }
}
