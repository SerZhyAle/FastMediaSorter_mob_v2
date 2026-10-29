package com.sza.fastmediasorter.core.launcher

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
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

    /**
     * Loads the preference file so a later [isEnabled] is a memory read. Call from a background
     * thread: the first read of a SharedPreferences file is a disk read, and the cold-start redirect
     * asks [isEnabled] on the main thread before the first frame (S4484).
     */
    fun preload() {
        if (!contract.isAvailableInBuild) return
        prefs().contains(KEY_START_WINDOW_ENABLED)
        Timber.d("S4484: launcher start-window prefs preloaded on thread ${Thread.currentThread().name}")
    }

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
