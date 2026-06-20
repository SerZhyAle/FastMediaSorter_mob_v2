package com.sza.fastmediasorter.core.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * Runtime detector for "this device is an XR / VR headset" (Meta Quest family, Android XR
 * spatial devices, generic VR-mode hardware).
 *
 * Lives in `src/main/java/` on purpose:
 * - Quest users sideload the **standard** APK (not the `vr` / `noLegal` flavor) and need the
 *   same guard. The flavor-only XR detector at
 *   `src/vr/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImpl.kt` is not
 *   available in `standard`/`lite`/`photos`/`legacy`, so a small mirror is required in `main`.
 * - This probe only reads [PackageManager.hasSystemFeature]; it does NOT touch any Oculus /
 *   Meta SDK class. There is no `BuildConfig` flavor guard and no compile-time dependency on
 *   any flavor-specific code. Strict Rule 15 (no `BuildConfig.IS_*` / `SUPPORT_*` gates in
 *   `src/main/`) is satisfied - system-feature checks are pure runtime.
 *
 * The feature list mirrors `XrEnvironmentDetectorImpl`: both modern Android XR features and
 * the legacy Oculus-prefixed features that retail Quest 3 firmware still exposes to regular
 * Android apps.
 */
object XrDeviceProbe {

    /**
     * Returns true when the current device exposes any known XR / VR system feature.
     * Cheap call (one [PackageManager.hasSystemFeature] hop per probed feature), but the
     * caller should still cache the answer if it is queried more than a few times per second.
     */
    fun isXrDevice(context: Context): Boolean {
        val pm = context.packageManager
        return XR_SYSTEM_FEATURES.any(pm::hasSystemFeature)
    }

    private val XR_SYSTEM_FEATURES = arrayOf(
        // Android XR (spatial OS, Samsung / Google reference devices).
        "android.software.xr.api.openxr",
        "android.software.xr.api.spatial",
        // Generic Android VR mode.
        PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE,
        "android.hardware.vr.headtracking",
        // Meta Quest (Horizon OS) - features exposed to ordinary Android apps on retail Quest 3.
        "oculus.hardware.standalone_vr",
        "oculus.software.spatial",
        "oculus.software.xrsp",
    )
}
