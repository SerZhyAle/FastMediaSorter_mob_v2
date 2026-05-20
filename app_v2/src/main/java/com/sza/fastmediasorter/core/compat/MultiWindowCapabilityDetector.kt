package com.sza.fastmediasorter.core.compat

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * S0184: conservative default for exposing separate-window actions.
 *
 * User preferences always override this detector; it only decides the first-run default.
 *
 * Tightened heuristic: only true desktop / XR / VR form factors get the default ON.
 * Samsung DeX and the generic `FEATURE_FREEFORM_WINDOW_MANAGEMENT` flag are intentionally
 * NOT consulted - on a regular Samsung phone DeX is declared by the platform but the user
 * is typically not in a multi-window context, so the "Open in new window" action would
 * appear without reason. Such users can still enable the toggle manually in
 * Settings -> Interface.
 */
object MultiWindowCapabilityDetector {
    private const val FEATURE_ANDROID_XR_IMMERSIVE = "android.software.xr.immersive"
    private const val FEATURE_ANDROID_XR_OPENXR = "android.software.xr.api.openxr"
    private const val FEATURE_VR_HEADTRACKING = "android.hardware.vr.headtracking"

    fun defaultAllowSeparateWindow(context: Context): Boolean {
        val packageManager = context.packageManager
        return ChromeOsCompat.isChromeOs(context) ||
            hasFeature(packageManager, FEATURE_ANDROID_XR_IMMERSIVE) ||
            hasFeature(packageManager, FEATURE_ANDROID_XR_OPENXR) ||
            hasFeature(packageManager, FEATURE_VR_HEADTRACKING) ||
            isKnownVrManufacturer()
    }

    private fun hasFeature(packageManager: PackageManager, feature: String): Boolean =
        runCatching { packageManager.hasSystemFeature(feature) }.getOrDefault(false)

    private fun isKnownVrManufacturer(): Boolean {
        val manufacturer = Build.MANUFACTURER
        return manufacturer.equals("Meta", ignoreCase = true) ||
            manufacturer.equals("Oculus", ignoreCase = true)
    }
}

