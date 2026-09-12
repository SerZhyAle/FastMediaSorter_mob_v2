package com.sza.fastmediasorter.core.launcher

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2811: durable flag for "open the launcher desktop as the app's own start window".
 *
 * The flag lives here beside the launcher-mode state rather than in the settings store because the
 * startup decision is taken in onCreate, before the settings flow has emitted its first value - waiting
 * for that emission would put a disk read into every cold start.
 *
 * It defaults to on, and it is inert on builds that compile no desktop surface: both accessors answer
 * for the build first, so a flavor without the launcher can neither report the mode on nor turn it on.
 */
@Singleton
class LauncherStartWindowManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contract: LauncherModeContract,
) {

    fun isEnabled(): Boolean {
        if (!contract.isAvailableInBuild) return false
        return prefs().getBoolean(KEY_START_WINDOW_ENABLED, true)
    }

    fun setEnabled(enabled: Boolean) {
        if (!contract.isAvailableInBuild) return
        prefs().edit().putBoolean(KEY_START_WINDOW_ENABLED, enabled).apply()
    }

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences(PREFS_LAUNCHER_START_WINDOW, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_LAUNCHER_START_WINDOW = "launcher_start_window_prefs"
        const val KEY_START_WINDOW_ENABLED = "start_window_enabled"
    }
}
