package com.sza.fastmediasorter.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowseStateDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private val FILTER_NAME_CONTAINS = stringPreferencesKey("filter_name_contains")
        private val FILTER_MIN_DATE = longPreferencesKey("filter_min_date")
        private val FILTER_MAX_DATE = longPreferencesKey("filter_max_date")
        private val FILTER_MIN_SIZE_MB = floatPreferencesKey("filter_min_size_mb")
        private val FILTER_MAX_SIZE_MB = floatPreferencesKey("filter_max_size_mb")
        private val FILTER_MEDIA_TYPES = stringSetPreferencesKey("filter_media_types")
    }

    val filter: Flow<FileFilter?> = dataStore.data.map { preferences ->
        val nameContains = preferences[FILTER_NAME_CONTAINS]
        val minDate = preferences[FILTER_MIN_DATE]
        val maxDate = preferences[FILTER_MAX_DATE]
        val minSizeMb = preferences[FILTER_MIN_SIZE_MB]
        val maxSizeMb = preferences[FILTER_MAX_SIZE_MB]
        val mediaTypesSet = preferences[FILTER_MEDIA_TYPES]

        if (nameContains == null && minDate == null && maxDate == null && 
            minSizeMb == null && maxSizeMb == null && mediaTypesSet == null) {
            null
        } else {
            val mediaTypes = mediaTypesSet?.mapNotNull { typeName ->
                try {
                    MediaType.valueOf(typeName)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }?.toSet()

            FileFilter(
                nameContains = nameContains,
                minDate = minDate,
                maxDate = maxDate,
                minSizeMb = minSizeMb,
                maxSizeMb = maxSizeMb,
                mediaTypes = mediaTypes
            )
        }
    }

    suspend fun saveFilter(filter: FileFilter?) {
        dataStore.edit { preferences ->
            if (filter == null || filter.isEmpty()) {
                preferences.remove(FILTER_NAME_CONTAINS)
                preferences.remove(FILTER_MIN_DATE)
                preferences.remove(FILTER_MAX_DATE)
                preferences.remove(FILTER_MIN_SIZE_MB)
                preferences.remove(FILTER_MAX_SIZE_MB)
                preferences.remove(FILTER_MEDIA_TYPES)
            } else {
                if (filter.nameContains != null) {
                    preferences[FILTER_NAME_CONTAINS] = filter.nameContains
                } else {
                    preferences.remove(FILTER_NAME_CONTAINS)
                }

                if (filter.minDate != null) {
                    preferences[FILTER_MIN_DATE] = filter.minDate
                } else {
                    preferences.remove(FILTER_MIN_DATE)
                }

                if (filter.maxDate != null) {
                    preferences[FILTER_MAX_DATE] = filter.maxDate
                } else {
                    preferences.remove(FILTER_MAX_DATE)
                }

                if (filter.minSizeMb != null) {
                    preferences[FILTER_MIN_SIZE_MB] = filter.minSizeMb
                } else {
                    preferences.remove(FILTER_MIN_SIZE_MB)
                }

                if (filter.maxSizeMb != null) {
                    preferences[FILTER_MAX_SIZE_MB] = filter.maxSizeMb
                } else {
                    preferences.remove(FILTER_MAX_SIZE_MB)
                }

                if (filter.mediaTypes != null) {
                    preferences[FILTER_MEDIA_TYPES] = filter.mediaTypes.map { it.name }.toSet()
                } else {
                    preferences.remove(FILTER_MEDIA_TYPES)
                }
            }
        }
    }

    suspend fun clearFilter() {
        saveFilter(null)
    }
}
