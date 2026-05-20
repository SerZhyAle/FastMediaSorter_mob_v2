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
 * 2. Meta Quest - accept both the public Android aliases and the Oculus-prefixed features
 *    seen on retail Quest 3 firmware where the aliases are not exposed to regular Android apps.
 * 3. Otherwise - [XrEnvironment.NONE].
 */
@Singleton
class XrEnvironmentDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : XrEnvironmentDetector {

    override fun detect(): XrEnvironment {
        val pm = context.packageManager
        return when {
            hasAnyFeature(
                pm,
                FEATURE_ANDROID_XR_OPENXR,
                FEATURE_ANDROID_XR_SPATIAL,
            ) -> XrEnvironment.ANDROID_XR
            hasAnyFeature(
                pm,
                PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE,
                FEATURE_QUEST_HEADTRACKING,
                FEATURE_OCULUS_STANDALONE_VR,
                FEATURE_OCULUS_SOFTWARE_SPATIAL,
                FEATURE_OCULUS_XRSP,
            ) -> XrEnvironment.VR_QUEST
            else -> XrEnvironment.NONE
        }
    }

    private fun hasAnyFeature(pm: PackageManager, vararg features: String): Boolean =
        features.any(pm::hasSystemFeature)

    private companion object {
        const val FEATURE_ANDROID_XR_OPENXR = "android.software.xr.api.openxr"
        const val FEATURE_ANDROID_XR_SPATIAL = "android.software.xr.api.spatial"
        const val FEATURE_QUEST_HEADTRACKING = "android.hardware.vr.headtracking"
        const val FEATURE_OCULUS_STANDALONE_VR = "oculus.hardware.standalone_vr"
        const val FEATURE_OCULUS_SOFTWARE_SPATIAL = "oculus.software.spatial"
        const val FEATURE_OCULUS_XRSP = "oculus.software.xrsp"
    }
}
