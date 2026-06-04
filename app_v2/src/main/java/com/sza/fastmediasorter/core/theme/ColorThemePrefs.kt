package com.sza.fastmediasorter.core.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import timber.log.Timber

/**
 * Synchronous mirror of [com.sza.fastmediasorter.domain.model.AppSettings.colorTheme] used to apply
 * the app-wide light/dark mode before any Activity inflates.
 *
 * The canonical value lives in DataStore (async), but [AppCompatDelegate.setDefaultNightMode] must be
 * called at process start - before the first Activity - so the choice is read here from a dedicated
 * SharedPreferences file. The Settings selector writes here in addition to DataStore right before
 * prompting for an app restart.
 *
 * Raw values: "AUTO" (follow device night-mode, default), "LIGHT" (force light), "DARK" (force dark).
 */
object ColorThemePrefs {
    private const val FILE_NAME = "color_theme_prefs"
    private const val KEY_COLOR_THEME = "color_theme"
    private const val DEFAULT = "AUTO"

    fun toNightMode(value: String): Int = when (value) {
        "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
        "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun setMode(context: Context, value: String) {
        context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COLOR_THEME, value)
            .apply()
    }

    /**
     * Apply [value] to the process-global night mode immediately.
     *
     * Required because the in-app "restart" only relaunches the Activity stack without killing the
     * process, so [applySavedMode] (called once at process start) never re-runs. Without this the new
     * theme would only take effect after the user force-kills and reopens the app.
     */
    fun applyMode(value: String) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(value))
    }

    fun applySavedMode(context: Context) {
        val value = context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COLOR_THEME, DEFAULT) ?: DEFAULT
        AppCompatDelegate.setDefaultNightMode(toNightMode(value))
        Timber.i("ColorThemePrefs: applied color theme mode=$value")
    }
}
