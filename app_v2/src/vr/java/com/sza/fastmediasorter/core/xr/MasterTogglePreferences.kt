package com.sza.fastmediasorter.core.xr

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vrMasterToggleStore by preferencesDataStore(name = "vr_master_toggle")

/**
 * DataStore-backed wrapper for the user master toggle that controls VR feature visibility.
 * Default value is derived from [XrEnvironmentDetector] at first read (ON for XR-capable
 * devices, OFF otherwise). Once the user touches it, the explicit value sticks even if the
 * device changes (e.g. debug build moved Quest 3 → phone).
 */
@Singleton
class MasterTogglePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detector: XrEnvironmentDetector,
) {

    val enabled: Flow<Boolean> = context.vrMasterToggleStore.data.map { prefs ->
        prefs[KEY] ?: defaultValue()
    }

    suspend fun setEnabled(value: Boolean) {
        context.vrMasterToggleStore.edit { prefs ->
            prefs[KEY] = value
        }
    }

    private fun defaultValue(): Boolean = when (detector.detect()) {
        XrEnvironment.VR_QUEST, XrEnvironment.ANDROID_XR -> true
        XrEnvironment.NONE -> false
    }

    private companion object {
        val KEY = booleanPreferencesKey("pref_vr_enable_3d")
    }
}
