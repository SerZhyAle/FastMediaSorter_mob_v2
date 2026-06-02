package com.sza.fastmediasorter.data.detector

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Utility object for Android platform signal detection.
 * Checks device features, UI mode, screen size, and manufacturer hints
 * to support device profile auto-detection.
 */
object DetectionHelper {

    /**
     * Check for VR/XR features: PackageManager features or UI mode.
     * Returns true if device reports VR mode or XR features.
     */
    fun hasVrFeatures(context: Context): Boolean {
        val pm = context.packageManager
        // Check for XR/VR features
        if (pm.hasSystemFeature("android.software.vr.mode")) return true
        if (pm.hasSystemFeature("android.software.vr_mode_high_perf")) return true
        if (pm.hasSystemFeature("android.xr.feature.openxr")) return true

        // Note: VR_HEADSET mode type not available in standard UiModeManager
        // Rely on feature flags instead

        return false
    }

    /**
     * Check for automotive feature (car head unit).
     */
    fun hasAutomotiveFeature(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return true

        // Car mode detection via features is more reliable than UiModeManager
        return false
    }

    /**
     * Check for TV / leanback feature (TV box, media box).
     */
    fun hasTelevisionFeature(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true

        return false
    }

    /**
     * Get smallest screen width in dp (Configuration.smallestScreenWidthDp).
     * Returns 0 if unavailable.
     */
    fun getSmallestWidthDp(context: Context): Int {
        return try {
            context.resources.configuration.smallestScreenWidthDp
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Check for telephony feature (phone capability).
     */
    fun hasTelephonyFeature(context: Context): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    /**
     * Get device manufacturer name (Build.MANUFACTURER).
     * Used for heuristic VR headset detection.
     */
    fun getManufacturer(): String {
        return android.os.Build.MANUFACTURER
    }

    /**
     * Check for known VR headset manufacturers.
     * Helps detect VR headsets that may not report official XR features.
     */
    fun isKnownVrHeadsetManufacturer(): Boolean {
        val manufacturer = getManufacturer().lowercase()
        return manufacturer.contains("meta") ||
               manufacturer.contains("oculus") ||
               manufacturer.contains("htc") ||
               manufacturer.contains("pico") ||
               manufacturer.contains("valve")
    }

    /**
     * Current UI mode type via [UiModeManager] - the strongest device-class signal Android exposes:
     * [Configuration.UI_MODE_TYPE_CAR] / [Configuration.UI_MODE_TYPE_TELEVISION] /
     * [Configuration.UI_MODE_TYPE_VR_HEADSET] / [Configuration.UI_MODE_TYPE_DESK] / NORMAL / WATCH.
     * Returns [Configuration.UI_MODE_TYPE_UNDEFINED] when unavailable.
     */
    fun getUiModeType(context: Context): Int = try {
        (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
            ?: Configuration.UI_MODE_TYPE_UNDEFINED
    } catch (e: Exception) {
        Configuration.UI_MODE_TYPE_UNDEFINED
    }

    /** Desktop-class device (declares `android.hardware.type.pc`). */
    fun hasPcFeature(context: Context): Boolean =
        context.packageManager.hasSystemFeature("android.hardware.type.pc")

    /** ChromeOS device running Android apps through ARC (the documented Chromebook signal). */
    fun isChromebook(context: Context): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature("org.chromium.arc") ||
               pm.hasSystemFeature("org.chromium.arc.device_management")
    }
}
