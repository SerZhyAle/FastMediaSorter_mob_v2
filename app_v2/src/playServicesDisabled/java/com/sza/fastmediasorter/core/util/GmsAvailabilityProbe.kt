package com.sza.fastmediasorter.core.util

import android.content.Context

/**
 * Inert Play Services probe for flavors built without the library (strategic S0403).
 *
 * Mounted via the `playServicesDisabled` source set; the `playServicesEnabled` twin under the same
 * FQCN carries the real check.
 *
 * [evaluate] answers UNAVAILABLE honestly, which is what a live caller wants - the launcher's
 * Google section, for one, must not be seeded on a build that cannot open those apps. What it must
 * NOT do is let that verdict reach the cached status behind [GmsAvailabilityChecker.check], because
 * `BaseActivity` raises a snackbar and `GeneralSettingsFragment` a settings row on any non-OK
 * cached status, both offering a `market://` link. On F-Droid that is an entry point to a removed
 * capability, which strategic goal 3 forbids outright. [IS_SUPPORTED] is the flag that stops it,
 * the same way the XR branch inside `check` already does for headsets without Play Services.
 */
internal object GmsAvailabilityProbe {

    const val IS_SUPPORTED: Boolean = false

    fun evaluate(context: Context, minApkVersion: Int): GmsAvailabilityChecker.Status =
        GmsAvailabilityChecker.Status.UNAVAILABLE
}
