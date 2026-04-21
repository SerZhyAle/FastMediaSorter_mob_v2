package com.sza.fastmediasorter.core.xr

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/**
 * Runtime detector for VR/XR headset devices (Quest, etc.).
 *
 * Placed in main source set so it works across ALL flavors (standard, lite, photos, legacy, vr).
 * No BuildConfig gate — the check itself is the gate; returns false on phones/tablets.
 *
 * Detection priority (highest reliability first):
 *  1. `android.hardware.vr.headtracking` — declared in /etc/permissions on all HorizonOS devices.
 *  2. `FEATURE_VR_MODE_HIGH_PERFORMANCE` — legacy Quest 1/2 fallback.
 *  3. Build.MANUFACTURER == "oculus" / "meta" — fallback for custom ROMs / sideloads.
 */
object XrDeviceDetector {

    /** Returns true if the running device is a VR/XR headset. */
    fun isXrHeadset(context: Context): Boolean {
        val pm = context.packageManager
        // Most reliable: explicit headtracking feature declared by HorizonOS
        if (pm.hasSystemFeature("android.hardware.vr.headtracking")) return true
        // Legacy Quest 1/2 fallback
        @Suppress("DEPRECATION")
        if (pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) return true
        // Final fallback: manufacturer name (covers custom proms / sideloaded builds)
        val mfr = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return mfr == "oculus" || mfr == "meta"
    }
}
