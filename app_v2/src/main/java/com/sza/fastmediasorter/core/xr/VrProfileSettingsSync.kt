package com.sza.fastmediasorter.core.xr

import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Flavor-isolated hook that aligns VR-related defaults after a device-profile preset is applied.
 *
 * Phone-only flavors keep this as a no-op. VR-capable flavors may coerce `disable3dVr` and sync
 * their separate runtime toggle storage so the Settings UI and VR entrypoints stay consistent
 * after the user switches profiles.
 */
interface VrProfileSettingsSync {
    suspend fun align(profileType: DeviceProfileType, settings: AppSettings): AppSettings
}
