package com.sza.fastmediasorter.core.xr

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real XR environment detector. Reads PackageManager system features. Synchronous: no I/O.
 *
 * Detection order:
 * 1. Android XR - `android.software.xr.api.openxr` OR `android.software.xr.api.spatial`.
 * 2. Meta Quest - `android.hardware.vr.headtracking` (Horizon OS mandates this since v62).
 * 3. Otherwise - [XrEnvironment.NONE].
 */
@Singleton
class XrEnvironmentDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : XrEnvironmentDetector {

    override fun detect(): XrEnvironment {
        val pm = context.packageManager
        return when {
            pm.hasSystemFeature(FEATURE_ANDROID_XR_OPENXR) ||
                pm.hasSystemFeature(FEATURE_ANDROID_XR_SPATIAL) -> XrEnvironment.ANDROID_XR
            pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE) ||
                pm.hasSystemFeature(FEATURE_QUEST_HEADTRACKING) -> XrEnvironment.VR_QUEST
            else -> XrEnvironment.NONE
        }
    }

    private companion object {
        const val FEATURE_ANDROID_XR_OPENXR = "android.software.xr.api.openxr"
        const val FEATURE_ANDROID_XR_SPATIAL = "android.software.xr.api.spatial"
        const val FEATURE_QUEST_HEADTRACKING = "android.hardware.vr.headtracking"
    }
}
