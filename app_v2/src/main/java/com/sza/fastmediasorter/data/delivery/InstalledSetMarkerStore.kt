package com.sza.fastmediasorter.data.delivery

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists per-[DeliverableSet] install state. The authoritative flag lives in the shared
 * `DataStore<Preferences>` (survives app update and cache clear); the downloaded payload lives
 * under `filesDir/delivery/<set>/` - deliberately outside the cache area so background cache
 * clearing never removes it (S0386 §3.2 data compatibility).
 */
@Singleton
class InstalledSetMarkerStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {

    /** Payload directory for [set]; the downloader stages `.so`/assets here. */
    fun payloadDir(set: DeliverableSet): File =
        File(File(context.filesDir, DELIVERY_DIR), set.name)

    private fun key(set: DeliverableSet) = booleanPreferencesKey("delivery_installed_${set.name}")

    fun installedFlagFlow(set: DeliverableSet): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[key(set)] ?: false }

    suspend fun setInstalled(set: DeliverableSet, installed: Boolean) {
        dataStore.edit { prefs -> prefs[key(set)] = installed }
    }

    /** Synchronous presence check used by the engine gate: the payload directory exists. */
    fun isPayloadPresent(set: DeliverableSet): Boolean = payloadDir(set).isDirectory

    /** Remove the downloaded payload to free space (uninstall from the extensions screen). */
    fun deletePayload(set: DeliverableSet) {
        payloadDir(set).deleteRecursively()
    }

    private companion object {
        const val DELIVERY_DIR = "delivery"
    }
}
