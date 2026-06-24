package com.sza.fastmediasorter.data.repository.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0659: last-session state of the "Трансляции" list (sort, media filter, search query) plus the last
 * catalog-refresh timestamp. This is ephemeral session memory, NOT a user setting, so it lives in its
 * own DataStore file ("streams_session") and is intentionally kept out of the main settings DataStore /
 * [com.sza.fastmediasorter.domain.model.AppSettings] (research H concern).
 *
 * Persisted sort/media-filter are raw enum `.name` strings: the ViewModel owns the enum<->name decode,
 * keeping this store contract-free so it never depends on a ViewModel or a domain enum.
 */
private val Context.streamsSessionDataStore by preferencesDataStore("streams_session")

@Singleton
class StreamsSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Snapshot of the last session. Null sort/media-filter/query mean "no remembered value" -> the
     * caller falls back to the user defaults. [lastCatalogRefreshAt] is epoch millis, 0 = never.
     */
    data class Session(
        val lastSort: String?,
        val lastMediaFilter: String?,
        val lastQuery: String?,
        val lastCatalogRefreshAt: Long,
    )

    suspend fun read(): Session {
        val prefs = context.streamsSessionDataStore.data.first()
        return Session(
            lastSort = prefs[KEY_LAST_SORT],
            lastMediaFilter = prefs[KEY_LAST_MEDIA_FILTER],
            lastQuery = prefs[KEY_LAST_QUERY],
            lastCatalogRefreshAt = prefs[KEY_LAST_CATALOG_REFRESH_AT] ?: 0L,
        )
    }

    suspend fun writeFilterState(sort: String, mediaFilter: String, query: String) {
        context.streamsSessionDataStore.edit { prefs ->
            prefs[KEY_LAST_SORT] = sort
            prefs[KEY_LAST_MEDIA_FILTER] = mediaFilter
            prefs[KEY_LAST_QUERY] = query
        }
    }

    suspend fun writeCatalogRefreshAt(epochMillis: Long) {
        context.streamsSessionDataStore.edit { prefs ->
            prefs[KEY_LAST_CATALOG_REFRESH_AT] = epochMillis
        }
    }

    private companion object {
        val KEY_LAST_SORT = stringPreferencesKey("last_sort")
        val KEY_LAST_MEDIA_FILTER = stringPreferencesKey("last_media_filter")
        val KEY_LAST_QUERY = stringPreferencesKey("last_query")
        val KEY_LAST_CATALOG_REFRESH_AT = longPreferencesKey("last_catalog_refresh_at")
    }
}
