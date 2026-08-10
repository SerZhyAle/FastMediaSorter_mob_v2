package com.sza.fastmediasorter.data.networkmonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * S1433: the permission that governs reaching Bluetooth on this API level.
 *
 * API 31 moved adapter and device access from the install-time `BLUETOOTH` permission to the runtime
 * `BLUETOOTH_CONNECT` one, so the name offered to the user follows the platform instead of being fixed at
 * one value. Shared by the feature's three Bluetooth readers: one platform rule kept in three private
 * copies only disagrees the day one of them is corrected.
 */
internal fun requiredBluetoothPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_CONNECT
    } else {
        LEGACY_BLUETOOTH_PERMISSION
    }

/** True when [requiredBluetoothPermission] is granted to this process. */
internal fun hasBluetoothAccess(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, requiredBluetoothPermission()) ==
        PackageManager.PERMISSION_GRANTED

/**
 * `Manifest.permission.BLUETOOTH`, named as a literal.
 *
 * The constant is deprecated from API 31, and referencing it would put a deprecation warning on a branch
 * that only ever runs below that level.
 */
private const val LEGACY_BLUETOOTH_PERMISSION = "android.permission.BLUETOOTH"
