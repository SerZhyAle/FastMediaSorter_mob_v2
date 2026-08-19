package com.sza.fastmediasorter.data

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber

/**
 * Isolated synchronous SharedPreferences access helper for platform entry points
 * (AppWidgetProvider and Glide cache limit setup) where async DataStore is not yet available.
 */
object SyncStorageCompat {

    private const val SYNC_PREFS_NAME = "sync_compat_prefs"
    private const val DEFAULT_CACHE_LIMIT_MB = 250

    fun getSyncPreferences(context: Context, name: String = SYNC_PREFS_NAME): SharedPreferences {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun getWidgetPref(context: Context, key: String, defaultValue: String): String {
        val prefs = getSyncPreferences(context)
        val value = prefs.getString(key, defaultValue) ?: defaultValue
        Timber.d("SyncStorageCompat: read widget pref key=$key value=$value")
        return value
    }

    fun getGlideCacheLimitMb(context: Context, defaultValueMb: Int = DEFAULT_CACHE_LIMIT_MB): Int {
        val prefs = getSyncPreferences(context)
        val limit = prefs.getInt("glide_disk_cache_limit_mb", defaultValueMb)
        Timber.d("SyncStorageCompat: read glide cache limit=$limit MB")
        return limit
    }
}
