package com.sza.fastmediasorter.core.xr

import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.AppSettings
import javax.inject.Inject

/**
 * Phone-only flavors do not expose the VR settings block, so profile application leaves VR
 * toggles untouched.
 */
class NoOpVrProfileSettingsSync @Inject constructor() : VrProfileSettingsSync {
    override suspend fun align(profileType: DeviceProfileType, settings: AppSettings): AppSettings = settings
}
