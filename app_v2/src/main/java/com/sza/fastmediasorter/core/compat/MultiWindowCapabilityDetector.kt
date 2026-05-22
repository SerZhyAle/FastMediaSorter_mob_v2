package com.sza.fastmediasorter.core.compat

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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

    fun defaultAllowSeparateWindow(context: Context): Boolean =
        hasInstallTimeMultiWindowSignal(context)

    /**
     * S0293: install-time default for the paired `fileOpsInOverflowMenu` preference.
     * The per-row ⋮ overflow button is required to expose multi-window entry points
     * (per-file "Open in new window") on capability-detected devices; without it the
     * action is unreachable. Symmetric to [defaultAllowSeparateWindow].
     */
    fun defaultFileOpsInOverflowMenu(context: Context): Boolean =
        hasInstallTimeMultiWindowSignal(context)

    /**
     * S0293 (ADR-3): runtime-aware effective multi-window capability for an Activity.
     *
     * Returns true when EITHER:
     *  - the device permanently exposes a multi-window UX (Quest 3, Android XR, ChromeOS,
     *    Meta/Oculus manufacturer) - the install-time signal set, OR
     *  - the Activity is currently in a desktop / DeX-mode UI container
     *    (`Configuration.UI_MODE_TYPE_DESK`).
     *
     * Conservative formula per S0293 §6.2 resolution: only `UI_MODE_TYPE_DESK` participates
     * as runtime signal; `isInMultiWindowMode()` is intentionally NOT consulted because it
     * also fires on phone split-screen, where multi-window UX is not meaningful.
     */
    fun isMultiWindowActiveNow(activity: Activity): Boolean {
        val installTime = hasInstallTimeMultiWindowSignal(activity)
        val uiModeType = activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val deskMode = uiModeType == Configuration.UI_MODE_TYPE_DESK
        timber.log.Timber.d("S0293: capability check runtime - install-time=$installTime, deskMode=$deskMode")
        return installTime || deskMode
    }

    private fun hasInstallTimeMultiWindowSignal(context: Context): Boolean {
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

