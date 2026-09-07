package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.wearSettingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "wear_settings")

/**
 * S2655: the single `wear_settings` store, held by one object so several classes can address it.
 *
 * The delegate is top-level rather than a member because `preferencesDataStore` builds a new store
 * per property owner: declared inside a class, a second instance of that class throws
 * `IllegalStateException: There are multiple DataStores active for the same file`. That was harmless
 * while one repository owned every setting and becomes fatal the moment the settings are split.
 *
 * A wrapper class rather than a `@Provides` on `DataStore<Preferences>`: a bare binding of that type
 * is a collision waiting for the next store the module gains, and a qualifier for one consumer costs
 * more than this class does.
 */
@Singleton
class WearSettingsDataStore @Inject constructor(
    @ApplicationContext context: Context
) {
    val store: DataStore<Preferences> = context.wearSettingsDataStore
}
