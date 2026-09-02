package com.sza.fastmediasorter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sza.fastmediasorter.domain.repository.LauncherShortcutSyncRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2330: stores the shortcut-sync baseline in the shared preferences DataStore from AppModule, the
 * same instance [com.sza.fastmediasorter.data.local.preferences.ReviewEligibilityDataStore] takes.
 *
 * Deliberately not in the launcher state table: the value is a flat set of route keys that never
 * participates in a query and is read and written whole, so putting it in the database would buy a
 * schema migration - the one irreversible change - for no relational gain (strategic ADR-3).
 *
 * Equally deliberately not in `AppSettings` / `LauncherSettingsStore`: this is the mechanism's own
 * bookkeeping, not a setting the user picks, and nothing should show it on a settings screen.
 */
@Singleton
class LauncherShortcutSyncRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LauncherShortcutSyncRepository {

    override suspend fun syncedRoutes(): Set<String>? =
        dataStore.data.first()[KEY_SYNCED_SHORTCUT_ROUTES]

    override suspend fun setSyncedRoutes(routeKeys: Set<String>) {
        // Copied rather than stored by reference: the caller's set is a live collection in the sync
        // pass, and DataStore keeps whatever it is handed.
        val snapshot = routeKeys.toSet()
        dataStore.edit { preferences -> preferences[KEY_SYNCED_SHORTCUT_ROUTES] = snapshot }
    }

    override suspend fun clearSyncedRoutes() {
        // remove(), not an empty set: the absent state is what makes the next run adopt the desktop
        // silently instead of treating every launchable route as newly enabled.
        dataStore.edit { preferences -> preferences.remove(KEY_SYNCED_SHORTCUT_ROUTES) }
    }

    private companion object {
        val KEY_SYNCED_SHORTCUT_ROUTES = stringSetPreferencesKey("launcher_synced_shortcut_routes")
    }
}
