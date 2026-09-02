package com.sza.fastmediasorter.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        private const val KEY_MIN_DATE = "filter_min_date"
        private const val KEY_MAX_DATE = "filter_max_date"
        private const val KEY_MIN_SIZE_MB = "filter_min_size_mb"
        private const val KEY_MAX_SIZE_MB = "filter_max_size_mb"
        private const val KEY_MEDIA_TYPES = "filter_media_types"

        // S2203: pre-fix keys carried no resourceId, so a filter set in one resource applied to
        // every other resource. Removed on the next saveFilter() call regardless of resourceId -
        // the old value is discarded, never migrated (see S2203 spec §6.3).
        private val LEGACY_MIN_DATE = longPreferencesKey(KEY_MIN_DATE)
        private val LEGACY_MAX_DATE = longPreferencesKey(KEY_MAX_DATE)
        private val LEGACY_MIN_SIZE_MB = floatPreferencesKey(KEY_MIN_SIZE_MB)
        private val LEGACY_MAX_SIZE_MB = floatPreferencesKey(KEY_MAX_SIZE_MB)
        private val LEGACY_MEDIA_TYPES = stringSetPreferencesKey(KEY_MEDIA_TYPES)
    }

    private fun minDateKey(resourceId: Long) = longPreferencesKey("${KEY_MIN_DATE}_$resourceId")
    private fun maxDateKey(resourceId: Long) = longPreferencesKey("${KEY_MAX_DATE}_$resourceId")
    private fun minSizeKey(resourceId: Long) = floatPreferencesKey("${KEY_MIN_SIZE_MB}_$resourceId")
    private fun maxSizeKey(resourceId: Long) = floatPreferencesKey("${KEY_MAX_SIZE_MB}_$resourceId")
    private fun mediaTypesKey(resourceId: Long) = stringSetPreferencesKey("${KEY_MEDIA_TYPES}_$resourceId")

    /** Filter persisted for [resourceId] only - never bleeds into a different resource (S2203). */
    fun filter(resourceId: Long): Flow<FileFilter?> = dataStore.data.map { preferences ->
        val minDate = preferences[minDateKey(resourceId)]
        val maxDate = preferences[maxDateKey(resourceId)]
        val minSizeMb = preferences[minSizeKey(resourceId)]
        val maxSizeMb = preferences[maxSizeKey(resourceId)]
        val mediaTypesSet = preferences[mediaTypesKey(resourceId)]

        if (listOf(minDate, maxDate, minSizeMb, maxSizeMb, mediaTypesSet).all { it == null }) {
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
                nameContains = null,
                minDate = minDate,
                maxDate = maxDate,
                minSizeMb = minSizeMb,
                maxSizeMb = maxSizeMb,
                mediaTypes = mediaTypes
            )
        }
    }

    suspend fun saveFilter(resourceId: Long, filter: FileFilter?) {
        dataStore.edit { preferences ->
            removeLegacyGlobalKeys(preferences)

            if (filter == null || filter.isEmpty()) {
                preferences.remove(minDateKey(resourceId))
                preferences.remove(maxDateKey(resourceId))
                preferences.remove(minSizeKey(resourceId))
                preferences.remove(maxSizeKey(resourceId))
                preferences.remove(mediaTypesKey(resourceId))
            } else {
                if (filter.minDate != null) {
                    preferences[minDateKey(resourceId)] = filter.minDate
                } else {
                    preferences.remove(minDateKey(resourceId))
                }

                if (filter.maxDate != null) {
                    preferences[maxDateKey(resourceId)] = filter.maxDate
                } else {
                    preferences.remove(maxDateKey(resourceId))
                }

                if (filter.minSizeMb != null) {
                    preferences[minSizeKey(resourceId)] = filter.minSizeMb
                } else {
                    preferences.remove(minSizeKey(resourceId))
                }

                if (filter.maxSizeMb != null) {
                    preferences[maxSizeKey(resourceId)] = filter.maxSizeMb
                } else {
                    preferences.remove(maxSizeKey(resourceId))
                }

                if (filter.mediaTypes != null) {
                    preferences[mediaTypesKey(resourceId)] = filter.mediaTypes.map { it.name }.toSet()
                } else {
                    preferences.remove(mediaTypesKey(resourceId))
                }
            }
        }
    }

    suspend fun clearFilter(resourceId: Long) {
        saveFilter(resourceId, null)
    }

    private fun removeLegacyGlobalKeys(preferences: MutablePreferences) {
        preferences.remove(LEGACY_MIN_DATE)
        preferences.remove(LEGACY_MAX_DATE)
        preferences.remove(LEGACY_MIN_SIZE_MB)
        preferences.remove(LEGACY_MAX_SIZE_MB)
        preferences.remove(LEGACY_MEDIA_TYPES)
    }
}
