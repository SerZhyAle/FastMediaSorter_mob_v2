package com.sza.fastmediasorter.core.debug

import android.content.Context
import com.sza.fastmediasorter.BuildConfig

object DebugFeatureFlags {

    private const val PREF_FILE = "debug_feature_flags"
    private const val KEY_LEAKCANARY_ENABLED = "leakcanary_enabled"

    fun isLeakCanaryEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_LEAKCANARY_ENABLED)) {
            return prefs.getBoolean(KEY_LEAKCANARY_ENABLED, false)
        }
        return BuildConfig.ENABLE_LEAKCANARY
    }

    fun setLeakCanaryEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LEAKCANARY_ENABLED, enabled)
            .apply()
    }
}