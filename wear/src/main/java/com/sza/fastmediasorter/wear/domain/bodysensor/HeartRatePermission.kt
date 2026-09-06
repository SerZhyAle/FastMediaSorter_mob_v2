package com.sza.fastmediasorter.wear.domain.bodysensor

import android.Manifest
import android.os.Build

/**
 * The heart-rate permission this platform version actually grants.
 *
 * API 36 replaced the sensor-shaped permission with a health-record one, and the `noLegal` manifest
 * declares both - the legacy entry capped at `maxSdkVersion="35"` (S2457 §6 item 2). The modern name is
 * spelled out because `android.Manifest.permission` carries no constant for it at compileSdk 36, while
 * `BODY_SENSORS` does; the pair is asymmetric in the platform, not in this file.
 *
 * Answered here rather than once per caller because two callers must agree: the screen REQUESTS a
 * permission and the measurement CHECKS one, so a split decision would grant a name the check never
 * reads and leave the diagnostic permanently on PERMISSION_DENIED. That failure would appear only above
 * API 36, which is the level the one test watch runs (S2457 §6 item 2), so the divergence would ship
 * looking like a broken sensor rather than like a wrong constant.
 *
 * Shared code names the permission but never declares it: the `uses-permission` entries stay in the
 * `noLegal` manifest alone (ADR-1), and a String here reaches no manifest.
 */
fun heartRatePermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
    PERMISSION_READ_HEART_RATE
} else {
    Manifest.permission.BODY_SENSORS
}

private const val PERMISSION_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
