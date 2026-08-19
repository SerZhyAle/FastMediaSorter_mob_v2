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

/**
 * Read counterpart of [setOrRemove]: the stored value, or [default] when the key is absent.
 *
 * A section store resolves its default inline as `preferences[KEY] ?: default`, and each of those
 * elvis operators is one branch. A wide section (20+ keys) therefore crosses the cyclomatic
 * complexity threshold on `read` alone, for no real branching - this keeps the count flat.
 */
internal fun <T> Preferences.getOrDefault(key: Preferences.Key<T>, default: T): T = this[key] ?: default
