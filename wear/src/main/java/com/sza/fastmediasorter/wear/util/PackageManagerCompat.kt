package com.sza.fastmediasorter.wear.util

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/**
 * S2458: the watch module's compat seam for the [PackageManager] query APIs whose raw-int flag overloads
 * were deprecated in API 33, mirroring `app_v2`'s `util/PackageManagerCompat.kt`.
 *
 * Only the overloads this module actually calls live here. The watch reads its own merged manifest to
 * learn whether this edition declares ACTIVITY_RECOGNITION, which needs `GET_PERMISSIONS`, and CLAUDE.md
 * Rule 21 refuses the raw-int call site that would otherwise be written inline.
 */

fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageInfo(packageName, flags)
    }

/** S3558: the face-slot providers ask whether a system screen has a real handler on this watch. */
fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: Int = 0): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        queryIntentActivities(intent, flags)
    }
