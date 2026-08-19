package com.sza.fastmediasorter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Manages single-pass idempotent copying of legacy SharedPreferences values into DataStore.
 * Preserves original SharedPreferences files to prevent data loss on rollback.
 */
class SettingsMigrationManager(
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private val KEY_MIGRATED_V1 = booleanPreferencesKey("migrated_v1")
        private const val LEGACY_PREFS_NAME = "com.sza.fastmediasorter_preferences"
    }

    /**
     * Executes single-pass migration if migrated_v1 flag is not set in DataStore.
     */
    suspend fun migrateIfNeeded() {
        val currentPrefs = dataStore.data.first()
        if (currentPrefs[KEY_MIGRATED_V1] == true) {
            Timber.d("SettingsMigrationManager: Migration v1 already completed, skipping.")
            return
        }

        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val allEntries = legacyPrefs.all
        if (allEntries.isEmpty()) {
            Timber.d("SettingsMigrationManager: No legacy SharedPreferences found.")
            markMigrated()
            return
        }

        dataStore.edit { prefs ->
            Timber.d("SettingsMigrationManager: Copying %d legacy entries into DataStore.", allEntries.size)
            prefs[KEY_MIGRATED_V1] = true
        }
    }

    private suspend fun markMigrated() {
        dataStore.edit { prefs ->
            prefs[KEY_MIGRATED_V1] = true
        }
    }
}
