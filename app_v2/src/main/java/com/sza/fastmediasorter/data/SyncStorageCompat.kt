package com.sza.fastmediasorter.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Isolated synchronous SharedPreferences access helper for platform entry points
 * (AppWidgetProvider) where async DataStore is not yet available.
 */
object SyncStorageCompat {

    private const val SYNC_PREFS_NAME = "sync_compat_prefs"

    fun getSyncPreferences(context: Context, name: String = SYNC_PREFS_NAME): SharedPreferences {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }
}
