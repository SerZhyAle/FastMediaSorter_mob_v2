package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.StreamDefaultSort
import com.sza.fastmediasorter.domain.model.StreamMediaTypeFilter
import com.sza.fastmediasorter.domain.model.StreamsCatalogRefreshPolicy

/**
 * Owns persistence of the Streams feature master switch (S0575) and the Streams default profile
 * (S0659: default sort, default media-type filter, catalog refresh policy). Extracted as its own
 * store so the Streams capability is a single named responsibility, mirroring [AudioSettingsStore].
 */
object StreamsSettingsStore {

    private val KEY_ENABLE_STREAMS = booleanPreferencesKey("enable_streams")
    private val KEY_DEFAULT_SORT = stringPreferencesKey("streams_default_sort")
    private val KEY_DEFAULT_MEDIA_FILTER = stringPreferencesKey("streams_default_media_filter")
    private val KEY_CATALOG_REFRESH_POLICY = stringPreferencesKey("streams_catalog_refresh_policy")

    /** Streams fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val enableStreams: Boolean,
        val streamsDefaultSort: StreamDefaultSort,
        val streamsDefaultMediaFilter: StreamMediaTypeFilter,
        val streamsCatalogRefreshPolicy: StreamsCatalogRefreshPolicy,
    )

    fun read(preferences: Preferences): Values = Values(
        enableStreams = preferences[KEY_ENABLE_STREAMS] ?: false,
        streamsDefaultSort = StreamDefaultSort.fromName(preferences[KEY_DEFAULT_SORT]),
        streamsDefaultMediaFilter = StreamMediaTypeFilter.fromName(preferences[KEY_DEFAULT_MEDIA_FILTER]),
        streamsCatalogRefreshPolicy = StreamsCatalogRefreshPolicy.fromName(preferences[KEY_CATALOG_REFRESH_POLICY]),
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_ENABLE_STREAMS] = settings.enableStreams
        preferences[KEY_DEFAULT_SORT] = settings.streamsDefaultSort.name
        preferences[KEY_DEFAULT_MEDIA_FILTER] = settings.streamsDefaultMediaFilter.name
        preferences[KEY_CATALOG_REFRESH_POLICY] = settings.streamsCatalogRefreshPolicy.name
    }
}
