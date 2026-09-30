package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "wear_video_prefs"
private const val KEY_BATTERY_WARNING_SHOWN = "battery_warning_shown"

/**
 * Whether the video player's first-run battery warning was dismissed. Kept in the file the player
 * used to read directly, so a warning dismissed before is not shown again. Both calls leave the
 * main thread: the first read of the file blocks until it is parsed from disk.
 */
@Singleton
class WearVideoBatteryWarningStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    suspend fun isShown(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_BATTERY_WARNING_SHOWN, false)
    }

    suspend fun markShown() {
        withContext(Dispatchers.IO) { prefs.edit().putBoolean(KEY_BATTERY_WARNING_SHOWN, true).apply() }
    }
}
