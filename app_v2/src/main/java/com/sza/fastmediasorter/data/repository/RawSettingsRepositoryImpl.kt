package com.sza.fastmediasorter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.data.repository.settings.RawPreferencesStore
import com.sza.fastmediasorter.domain.model.BackupPreference
import com.sza.fastmediasorter.domain.repository.RawSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** S3130: reads and writes the same settings DataStore [SettingsRepositoryImpl] owns, entry by entry. */
@Singleton
class RawSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : RawSettingsRepository {

    override suspend fun exportAll(): List<BackupPreference> =
        RawPreferencesStore.export(dataStore.data.first())

    override suspend fun importAll(values: List<BackupPreference>) {
        if (values.isEmpty()) return
        dataStore.edit { preferences -> RawPreferencesStore.apply(preferences, values) }
    }
}
