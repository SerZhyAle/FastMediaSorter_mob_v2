package com.sza.fastmediasorter.core.theme

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.util.Locale

/**
 * Synchronous mirror of [com.sza.fastmediasorter.domain.model.AppSettings.colorTheme] used to apply
 * the app-wide light/dark mode before any Activity inflates.
 *
 * The canonical value lives in DataStore (async), but [AppCompatDelegate.setDefaultNightMode] must be
 * called at process start - before the first Activity - so the choice is read here from a dedicated
 * SharedPreferences file. The Settings selector writes here in addition to DataStore right before
 * prompting for an app restart.
 *
 * Raw values: "AUTO" (follow device night-mode, default), "LIGHT" (force light), "DARK" (force dark),
 * plus the six S0569 custom accent themes "DARK_GREEN" / "DARK_BLUE" / "DARK_RED" / "LIGHT_GREEN" /
 * "LIGHT_BLUE" / "LIGHT_RED" - each pins a fixed night mode (dark/light) and is rendered by a
 * ThemeOverlay applied per-Activity via [applyThemeOverlay].
 */
object ColorThemePrefs {
    private const val FILE_NAME = "color_theme_prefs"
    private const val KEY_COLOR_THEME = "color_theme"
    private const val DEFAULT = "AUTO"

    fun normalizeValue(value: String?): String = when (value?.trim()?.uppercase(Locale.ROOT)) {
        "LIGHT" -> "LIGHT"
        "DARK" -> "DARK"
        "DARK_GREEN" -> "DARK_GREEN"
        "DARK_BLUE" -> "DARK_BLUE"
        "DARK_RED" -> "DARK_RED"
        "LIGHT_GREEN" -> "LIGHT_GREEN"
        "LIGHT_BLUE" -> "LIGHT_BLUE"
        "LIGHT_RED" -> "LIGHT_RED"
        else -> DEFAULT
    }

    fun toNightMode(value: String): Int = when (normalizeValue(value)) {
        "LIGHT", "LIGHT_GREEN", "LIGHT_BLUE", "LIGHT_RED" -> AppCompatDelegate.MODE_NIGHT_NO
        "DARK", "DARK_GREEN", "DARK_BLUE", "DARK_RED" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun setMode(context: Context, value: String) {
        val normalizedValue = normalizeValue(value)
        context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COLOR_THEME, normalizedValue)
            .apply()
    }

    /** Current normalized mode ("AUTO"|"LIGHT"|"DARK") from the synchronous mirror; used by callers
     *  that need to pre-select a control without waiting for the async DataStore value. */
    fun getMode(context: Context): String = normalizeValue(
        context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COLOR_THEME, DEFAULT)
    )

    /**
     * Apply [value] to the process-global night mode immediately.
     *
     * Required because the in-app "restart" only relaunches the Activity stack without killing the
     * process, so [applySavedMode] (called once at process start) never re-runs. Without this the new
     * theme would only take effect after the user force-kills and reopens the app.
     */
    fun applyMode(value: String) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(normalizeValue(value)))
    }

    /**
     * Layer the accent ThemeOverlay for the saved custom theme onto [activity]'s theme. Called from
     * [com.sza.fastmediasorter.core.ui.BaseActivity.onCreate] before the content view inflates, at the
     * same seam the compact-dialog overlay uses, so every view and dialog resolves the custom colors.
     * The base AUTO/LIGHT/DARK themes carry no overlay - the DayNight base theme is already correct.
     */
    fun applyThemeOverlay(activity: Activity) {
        val overlayResId = when (getMode(activity)) {
            "DARK_GREEN" -> R.style.ThemeOverlay_FastMediaSorter_DarkGreen
            "DARK_BLUE" -> R.style.ThemeOverlay_FastMediaSorter_DarkBlue
            "DARK_RED" -> R.style.ThemeOverlay_FastMediaSorter_DarkRed
            "LIGHT_GREEN" -> R.style.ThemeOverlay_FastMediaSorter_LightGreen
            "LIGHT_BLUE" -> R.style.ThemeOverlay_FastMediaSorter_LightBlue
            "LIGHT_RED" -> R.style.ThemeOverlay_FastMediaSorter_LightRed
            else -> return
        }
        Timber.d("S2557: applyThemeOverlay mode=%s", getMode(activity))
        activity.theme.applyStyle(overlayResId, true)
    }

    /**
     * Clear the synchronous theme mirror and force the process back to the default (follow-system)
     * night mode. Used by the debug "reset preferences" tool so a reset returns the app to a true
     * clean state instead of keeping the previously forced light/dark mode.
     */
    fun reset(context: Context) {
        context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        AppCompatDelegate.setDefaultNightMode(toNightMode(DEFAULT))
    }

    fun applySavedMode(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val rawValue = prefs.getString(KEY_COLOR_THEME, DEFAULT)
        val value = normalizeValue(rawValue)
        if (rawValue != value) {
            prefs.edit().putString(KEY_COLOR_THEME, value).apply()
        }
        AppCompatDelegate.setDefaultNightMode(toNightMode(value))
        Timber.i("ColorThemePrefs: applied color theme mode=$value")
    }
}
