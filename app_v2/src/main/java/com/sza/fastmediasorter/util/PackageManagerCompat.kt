package com.sza.fastmediasorter.util

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/**
 * S0467: single compat seam for the [PackageManager] query APIs whose raw-int flag overloads were
 * deprecated in API 33 (Tiramisu) in favor of the type-safe `*Flags.of(Long)` overloads.
 *
 * Centralizing the one `Build.VERSION` branch here keeps call sites free of `@Suppress("DEPRECATION")`
 * and stops new code from copying the deprecated raw-int pattern. The grep gate
 * `scripts/quality/assert-deprecated-pm-flags.ps1` keeps `src/main` at zero raw-int call sites (this
 * file is the only allow-listed exception).
 */

@Suppress("DEPRECATION")
fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageInfo(packageName, flags)
    }

@Suppress("DEPRECATION")
fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int = 0): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
    } else {
        getApplicationInfo(packageName, flags)
    }

@Suppress("DEPRECATION")
fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: Int = 0): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        queryIntentActivities(intent, flags)
    }

@Suppress("DEPRECATION")
fun PackageManager.resolveActivityCompat(intent: Intent, flags: Int = 0): ResolveInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        resolveActivity(intent, flags)
    }
