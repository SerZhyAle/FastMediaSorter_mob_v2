package com.sza.fastmediasorter.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.stats.StatsBaselineSnapshot
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Always-on baseline counters for usage statistics (S0473).
 *
 * Written unconditionally on every app launch, independent of the opt-in toggle - the first
 * launch moment cannot be reconstructed later, so it must be captured from the very first run
 * (strategic ADR-2 / §5.1). Keys are namespaced with `stats_baseline_` and live on the shared
 * `DataStore<Preferences>` from AppModule. Survives a detailed-stats wipe.
 */
@Singleton
class StatsBaselineDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        val FIRST_LAUNCH_EPOCH    = longPreferencesKey("stats_baseline_first_launch_epoch")
        val FIRST_INSTALL_VERSION = stringPreferencesKey("stats_baseline_first_install_version")
        val FIRST_INSTALL_FLAVOR  = stringPreferencesKey("stats_baseline_first_install_flavor")
        val LAUNCH_COUNT          = longPreferencesKey("stats_baseline_launch_count")
    }

    /**
     * Records one app launch: increments the launch counter, and on the very first call also
     * stamps the first-launch epoch and the install version/flavor (first write wins - later
     * launches never overwrite them).
     */
    suspend fun recordLaunch(installVersion: String, installFlavor: String) {
        dataStore.edit { prefs ->
            prefs[LAUNCH_COUNT] = (prefs[LAUNCH_COUNT] ?: 0L) + 1L
            if (prefs[FIRST_LAUNCH_EPOCH] == null) {
                prefs[FIRST_LAUNCH_EPOCH] = System.currentTimeMillis()
            }
            if (prefs[FIRST_INSTALL_VERSION] == null) {
                prefs[FIRST_INSTALL_VERSION] = installVersion
            }
            if (prefs[FIRST_INSTALL_FLAVOR] == null) {
                prefs[FIRST_INSTALL_FLAVOR] = installFlavor
            }
        }
    }

    /** One-shot snapshot of the baseline metrics (zero/empty defaults if never written). */
    suspend fun snapshot(): StatsBaselineSnapshot {
        val prefs = dataStore.data.first()
        return StatsBaselineSnapshot(
            firstLaunchEpochMs  = prefs[FIRST_LAUNCH_EPOCH] ?: 0L,
            firstInstallVersion = prefs[FIRST_INSTALL_VERSION] ?: "",
            firstInstallFlavor  = prefs[FIRST_INSTALL_FLAVOR] ?: "",
            launchCount         = prefs[LAUNCH_COUNT] ?: 0L
        )
    }
}
