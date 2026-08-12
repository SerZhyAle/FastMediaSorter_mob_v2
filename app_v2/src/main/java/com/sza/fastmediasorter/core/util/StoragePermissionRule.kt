package com.sza.fastmediasorter.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * The single owner of "which storage permission this SDK level needs".
 *
 * Every window below is the one the permission registry already declares for its STORAGE rows in
 * `PermissionRegistryRepositoryImpl`, so the rule follows the registry instead of restating it:
 * `read_external_storage` runs to `maxSdk = 32`, `write_external_storage` to `maxSdk = 28`, the
 * three `read_media_*` rows start at `minSdk = 33`, and `manage_external_storage` at `minSdk = 30`.
 */
object StoragePermissionRule {

    /** Registry row `read_media_*`: `minSdk = 33`, which is also where `read_external_storage` ends. */
    private const val READ_MEDIA_MIN_SDK = Build.VERSION_CODES.TIRAMISU

    /** Registry row `write_external_storage`: `maxSdk = 28`, the manifest's own upper bound. */
    private const val WRITE_EXTERNAL_STORAGE_MAX_SDK = Build.VERSION_CODES.P

    /**
     * Storage permissions that must be requested through a runtime dialog on [sdkInt].
     *
     * Empty below API 23, where storage is granted at install time; empty of write above API 28,
     * where the platform stops granting it.
     */
    fun requiredPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> = when {
        sdkInt >= READ_MEDIA_MIN_SDK -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )

        sdkInt > WRITE_EXTERNAL_STORAGE_MAX_SDK -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        sdkInt >= Build.VERSION_CODES.M -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )

        else -> emptyArray()
    }

    /**
     * True when full storage access on this device is answered by all-files access on a system
     * screen rather than by the runtime permissions [requiredPermissions] returns.
     */
    fun requiresAllFilesAccess(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= Build.VERSION_CODES.R

    /**
     * Whether every storage permission this SDK level needs is currently held. The platform check
     * is written against `Build.VERSION_CODES.R` rather than [requiresAllFilesAccess] because
     * `Environment.isExternalStorageManager()` is an API 30 call lint resolves only through the
     * literal comparison; the two express the same window.
     */
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager()
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
