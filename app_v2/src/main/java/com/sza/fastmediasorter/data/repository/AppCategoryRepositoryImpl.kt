package com.sza.fastmediasorter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.data.repository.settings.AppCategoryCatalogCodec
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.repository.AppCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4530: keeps the catalog as one value in the settings DataStore.
 *
 * That store and not Room: `RawPreferencesStore` exports every key it does not exclude, so the catalog
 * reaches the backup's raw settings section and survives a restore with no payload change, and a
 * backup that predates the key restores nothing over it.
 */
@Singleton
class AppCategoryRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppCategoryRepository {

    override fun observeCatalog(): Flow<AppCategoryCatalog> = dataStore.data
        .map { AppCategoryCatalogCodec.decode(it[KEY_CATALOG]) }
        .distinctUntilChanged()

    override suspend fun update(
        transform: (AppCategoryCatalog) -> AppCategoryCatalog,
    ): Result<AppCategoryCatalog> = try {
        var result = AppCategoryCatalog.EMPTY
        dataStore.edit { preferences ->
            val current = AppCategoryCatalogCodec.decode(preferences[KEY_CATALOG])
            result = transform(current)
            if (result != current) preferences[KEY_CATALOG] = AppCategoryCatalogCodec.encode(result)
        }
        Result.success(result)
    } catch (e: IOException) {
        Timber.w(e, "App category catalog not saved")
        Result.failure(e)
    }

    private companion object {
        val KEY_CATALOG = stringPreferencesKey("launcher_app_categories_v1")
    }
}
