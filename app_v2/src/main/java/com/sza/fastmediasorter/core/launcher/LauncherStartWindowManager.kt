package com.sza.fastmediasorter.core.launcher

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the app-only desktop start choice for builds that ship the desktop surface. */
@Singleton
class LauncherStartWindowManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contract: LauncherModeContract,
) {
    fun isEnabled(): Boolean =
        contract.isAvailableInBuild && prefs().getBoolean(KEY_START_WINDOW_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        if (contract.isAvailableInBuild) prefs().edit().putBoolean(KEY_START_WINDOW_ENABLED, enabled).apply()
    }

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences(PREFS_LAUNCHER_START_WINDOW, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_LAUNCHER_START_WINDOW = "launcher_start_window_prefs"
        const val KEY_START_WINDOW_ENABLED = "start_window_enabled"
    }
}
