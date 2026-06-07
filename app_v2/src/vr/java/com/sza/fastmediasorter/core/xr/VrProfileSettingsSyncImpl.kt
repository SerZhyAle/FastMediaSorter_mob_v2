package com.sza.fastmediasorter.core.xr

import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VR-capable flavors (`vr`, `noLegal`) keep the 3D/VR master toggle aligned with the selected
 * device profile. Non-VR profiles default the block to OFF but still leave it visible in Settings,
 * while the dedicated VR headset profile restores the ON default.
 */
@Singleton
class VrProfileSettingsSyncImpl @Inject constructor(
    private val preferences: MasterTogglePreferences,
) : VrProfileSettingsSync {

    override suspend fun align(profileType: DeviceProfileType, settings: AppSettings): AppSettings {
        val vrEnabledByProfile = profileType == DeviceProfileType.VR_HEADSET
        preferences.setEnabled(vrEnabledByProfile)
        return settings.copy(disable3dVr = !vrEnabledByProfile)
    }
}
