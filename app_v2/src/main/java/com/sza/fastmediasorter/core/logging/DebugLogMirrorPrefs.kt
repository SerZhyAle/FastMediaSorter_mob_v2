package com.sza.fastmediasorter.core.logging

import android.content.Context

/**
 * Opt-in flag for the debug-only mirroring of the session log into the folder currently opened in
 * the viewer ([LoggingHelper.updateDebugMirrorTargetFromPath]).
 *
 * Kept in a dedicated SharedPreferences file rather than in
 * [com.sza.fastmediasorter.domain.model.AppSettings]: [LoggingHelper] is a plain object initialised
 * at process start, before the Hilt graph and the asynchronous settings store exist, so it can only
 * read a synchronous mirror. Same constraint and same shape as
 * [com.sza.fastmediasorter.core.theme.ColorThemePrefs].
 *
 * Default is `false` - S1357: the mirror used to run on every debug build and left a
 * `fastmediasorter_debug_live.log` in every media folder the owner browsed.
 */
object DebugLogMirrorPrefs {
    private const val FILE_NAME = "debug_log_mirror_prefs"
    private const val KEY_MIRROR_ENABLED = "mirror_enabled"
    private const val DEFAULT = false

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MIRROR_ENABLED, DEFAULT)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MIRROR_ENABLED, enabled)
            .apply()
    }
}
