package com.sza.fastmediasorter.core.compat

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * S0184: conservative default for exposing separate-window actions.
 *
 * User preferences always override this detector; it only decides the first-run default.
 */
object MultiWindowCapabilityDetector {
    private const val FEATURE_ANDROID_XR_IMMERSIVE = "android.software.xr.immersive"
    private const val FEATURE_ANDROID_XR_OPENXR = "android.software.xr.api.openxr"
    private const val FEATURE_VR_HEADTRACKING = "android.hardware.vr.headtracking"
    private const val FEATURE_SAMSUNG_DEX = "com.samsung.android.feature.DESKTOP_MODE"
    private const val FEATURE_SAMSUNG_DEX_LEGACY = "com.sec.feature.desktopmode"

    fun defaultAllowSeparateWindow(context: Context): Boolean {
        val packageManager = context.packageManager
        return ChromeOsCompat.isChromeOs(context) ||
            hasFeature(packageManager, PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            hasFeature(packageManager, FEATURE_ANDROID_XR_IMMERSIVE) ||
            hasFeature(packageManager, FEATURE_ANDROID_XR_OPENXR) ||
            hasFeature(packageManager, FEATURE_VR_HEADTRACKING) ||
            hasFeature(packageManager, FEATURE_SAMSUNG_DEX) ||
            hasFeature(packageManager, FEATURE_SAMSUNG_DEX_LEGACY) ||
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

