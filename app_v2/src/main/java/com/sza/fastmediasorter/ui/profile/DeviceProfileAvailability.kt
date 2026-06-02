package com.sza.fastmediasorter.ui.profile

import com.sza.fastmediasorter.data.model.DeviceProfileType

/**
 * Flavor-gated set of device profiles offered to the user.
 *
 * The VR headset profile must appear only in VR-capable builds (`vr`, `noLegal`) and stay hidden in
 * `standard`/`lite`/`photos`/`legacy`. Per the flavor-isolation rules (no `BuildConfig` flavor guards
 * in `src/main`), the concrete list is provided by a flavor-specific Hilt module:
 * `src/vrStub/.../di/VrStubDeviceProfileAvailabilityModule` (no VR) vs
 * `src/vr/.../di/VrDeviceProfileAvailabilityModule` (with VR).
 */
interface DeviceProfileAvailability {

    /** Selectable profiles for this build, in display order. */
    val selectableProfiles: List<DeviceProfileType>

    fun isAvailable(type: DeviceProfileType): Boolean = type in selectableProfiles
}
