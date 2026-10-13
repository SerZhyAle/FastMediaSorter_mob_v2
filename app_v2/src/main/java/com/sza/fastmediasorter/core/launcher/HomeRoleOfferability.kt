package com.sza.fastmediasorter.core.launcher

import android.content.Context
import com.sza.fastmediasorter.core.compat.DesktopPlatformProbe
import com.sza.fastmediasorter.core.util.XrDeviceProbe

/**
 * Whether the device-home role can be offered at all on this device.
 *
 * Not on XR, where the system role has no use (S3123), and not on a Googlebook, whose platform
 * admits only its own launcher: "Googlebook supports only native system input methods and
 * launchers". Every surface that shows a HOME control asks this one rule, so they cannot drift.
 */
object HomeRoleOfferability {

    fun isOfferable(context: Context): Boolean =
        !XrDeviceProbe.isXrDevice(context) && !DesktopPlatformProbe.isPcFormFactor(context)
}
