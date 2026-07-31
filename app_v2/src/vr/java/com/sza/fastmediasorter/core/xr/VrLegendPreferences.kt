package com.sza.fastmediasorter.core.xr

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vrLegendStore by preferencesDataStore(name = "vr_legend")

/**
 * S1223: remembers whether the one-time immersive controls legend has already been shown.
 *
 * The flag lives in app storage, so it survives process death and every later immersive entry but
 * not a reinstall - "shown once per install" is the acceptance criterion, not a side effect of
 * where the value happens to sit. Kept apart from [MasterTogglePreferences] because that store is
 * named after the master toggle and a legend key inside it would be findable only by accident.
 */
@Singleton
class VrLegendPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun isShown(): Boolean = context.vrLegendStore.data.map { prefs -> prefs[KEY] ?: false }.first()

    suspend fun markShown() {
        context.vrLegendStore.edit { prefs -> prefs[KEY] = true }
    }

    private companion object {
        val KEY = booleanPreferencesKey("pref_vr_legend_shown")
    }
}
