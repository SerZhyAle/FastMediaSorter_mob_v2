package com.sza.fastmediasorter.core.xr

/**
 * Returns the runtime XR environment of the host device.
 *
 * Implementations:
 * - `src/vrStub/java/.../core/xr/NoOpXrEnvironmentDetector` - always [XrEnvironment.NONE].
 * - `src/vr/java/.../core/xr/XrEnvironmentDetectorImpl` - reads PackageManager features.
 */
interface XrEnvironmentDetector {
    fun detect(): XrEnvironment
}
