package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences

/**
 * Shared helper for the per-domain settings section stores: write a value, or
 * remove the key when the value is null. Mirrors the long-standing private helper
 * in SettingsRepositoryImpl so extracted sections keep identical persistence
 * semantics for nullable settings (e.g. optional destination resource ids).
 */
internal fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value != null) this[key] = value else remove(key)
}
