package com.sza.fastmediasorter.data.delivery

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    // S1200: which payload was installed, as the descriptor's stamp. It lives in the same DataStore as
    // the flag above precisely because it must share the flag's lifetime - surviving app update and
    // cache clear, and dying with the payload.
    private fun stampKey(set: DeliverableSet) = stringPreferencesKey("delivery_stamp_${set.name}")

    fun installedFlagFlow(set: DeliverableSet): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[key(set)] ?: false }

    suspend fun setInstalled(set: DeliverableSet, installed: Boolean) {
        dataStore.edit { prefs -> prefs[key(set)] = installed }
    }

    /** Records the descriptor stamp the freshly installed payload was fetched against. */
    suspend fun setStamp(set: DeliverableSet, stamp: String) {
        dataStore.edit { prefs -> prefs[stampKey(set)] = stamp }
    }

    /**
     * The recorded stamp, or null when nothing was ever recorded for [set]. Suspending rather than
     * blocking: every caller already reads this from a coroutine, and a DataStore read on the main
     * thread is exactly the kind of I/O the audit protocol forbids.
     */
    suspend fun installedStamp(set: DeliverableSet): String? =
        dataStore.data.first()[stampKey(set)]

    suspend fun clearStamp(set: DeliverableSet) {
        dataStore.edit { prefs -> prefs.remove(stampKey(set)) }
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
