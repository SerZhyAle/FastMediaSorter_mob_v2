package com.sza.fastmediasorter.data.repository.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
 *
 * S0697: category/language facet selections and the pinned-only toggle are persisted too, so the full
 * filter (not just sort/media/query) survives a restart. S0699: the first-visible list position is
 * remembered so the next open lands the user on the same channel.
 */
private val Context.streamsSessionDataStore by preferencesDataStore("streams_session")

@Singleton
class StreamsSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Snapshot of the last session. Null sort/media-filter/query/category/language/pinned mean "no
     * remembered value" -> the caller falls back to the user defaults. [lastCatalogRefreshAt] is epoch
     * millis, 0 = never. [lastScrollPosition] is the first-visible adapter position, null = top/none.
     */
    data class Session(
        val lastSort: String?,
        val lastMediaFilter: String?,
        val lastQuery: String?,
        val lastCategory: String?,
        val lastLanguage: String?,
        val lastPinnedOnly: Boolean?,
        val lastCatalogRefreshAt: Long,
        val lastDisplayMode: String?,
        val lastScrollPosition: Int?,
    )

    suspend fun read(): Session {
        val prefs = context.streamsSessionDataStore.data.first()
        return Session(
            lastSort = prefs[KEY_LAST_SORT],
            lastMediaFilter = prefs[KEY_LAST_MEDIA_FILTER],
            lastQuery = prefs[KEY_LAST_QUERY],
            lastCategory = prefs[KEY_LAST_CATEGORY],
            lastLanguage = prefs[KEY_LAST_LANGUAGE],
            lastPinnedOnly = prefs[KEY_LAST_PINNED_ONLY],
            lastCatalogRefreshAt = prefs[KEY_LAST_CATALOG_REFRESH_AT] ?: 0L,
            lastDisplayMode = prefs[KEY_LAST_DISPLAY_MODE],
            lastScrollPosition = prefs[KEY_LAST_SCROLL_POSITION],
        )
    }

    /**
     * S0697: persist the full filter + sort. Category/language are nullable facet values; absent (null)
     * keys are removed so a cleared facet does not linger across restarts.
     */
    suspend fun writeFilterState(
        sort: String,
        mediaFilter: String,
        query: String,
        category: String?,
        language: String?,
        pinnedOnly: Boolean,
    ) {
        context.streamsSessionDataStore.edit { prefs ->
            prefs[KEY_LAST_SORT] = sort
            prefs[KEY_LAST_MEDIA_FILTER] = mediaFilter
            prefs[KEY_LAST_QUERY] = query
            if (category != null) prefs[KEY_LAST_CATEGORY] = category else prefs.remove(KEY_LAST_CATEGORY)
            if (language != null) prefs[KEY_LAST_LANGUAGE] = language else prefs.remove(KEY_LAST_LANGUAGE)
            prefs[KEY_LAST_PINNED_ONLY] = pinnedOnly
        }
    }

    suspend fun writeCatalogRefreshAt(epochMillis: Long) {
        context.streamsSessionDataStore.edit { prefs ->
            prefs[KEY_LAST_CATALOG_REFRESH_AT] = epochMillis
        }
    }

    suspend fun writeDisplayMode(mode: String) {
        context.streamsSessionDataStore.edit { prefs ->
            prefs[KEY_LAST_DISPLAY_MODE] = mode
        }
    }

    /** S0699: remember the first-visible list position so the next open restores the user's place. */
    suspend fun writeScrollPosition(position: Int) {
        context.streamsSessionDataStore.edit { prefs ->
            prefs[KEY_LAST_SCROLL_POSITION] = position
        }
    }

    private companion object {
        val KEY_LAST_SORT = stringPreferencesKey("last_sort")
        val KEY_LAST_MEDIA_FILTER = stringPreferencesKey("last_media_filter")
        val KEY_LAST_QUERY = stringPreferencesKey("last_query")
        val KEY_LAST_CATEGORY = stringPreferencesKey("last_category")
        val KEY_LAST_LANGUAGE = stringPreferencesKey("last_language")
        val KEY_LAST_PINNED_ONLY = booleanPreferencesKey("last_pinned_only")
        val KEY_LAST_CATALOG_REFRESH_AT = longPreferencesKey("last_catalog_refresh_at")
        val KEY_LAST_DISPLAY_MODE = stringPreferencesKey("last_display_mode")
        val KEY_LAST_SCROLL_POSITION = intPreferencesKey("last_scroll_position")
    }
}
