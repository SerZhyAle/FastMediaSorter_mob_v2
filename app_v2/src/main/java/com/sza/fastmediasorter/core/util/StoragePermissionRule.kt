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
     *
     * S2012: the SDK window alone is no longer the answer. A build whose merged manifest lacks
     * `MANAGE_EXTERNAL_STORAGE` cannot obtain the grant however new the platform is, so treating the
     * window as sufficient made every store flavor ask forever for something it structurally could
     * not get.
     */
    fun requiresAllFilesAccess(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        requiresAllFilesAccess(sdkInt, AllFilesAccessDeclaration.isDeclared(context))

    /**
     * The rule itself, with both inputs supplied. Kept separate from the [Context] overload so the
     * whole matrix - two SDK windows against two manifests - is pinned by a test that needs no
     * device and no package manager, which is why [requiredPermissions] takes `sdkInt` too.
     */
    fun requiresAllFilesAccess(sdkInt: Int, declaresAllFilesAccess: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.R && declaresAllFilesAccess

    /**
     * True when the direct `java.io.File` route to shared storage is out of reach for good: from
     * API 30 the platform opens it only through all-files access, and a build that never declares
     * the permission has no way to ask. Below API 30 the runtime read permission still opens it, so
     * the direct route stays - which is why this is not simply the negation of
     * [requiresAllFilesAccess].
     */
    fun isDirectFileAccessUnobtainable(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        isDirectFileAccessUnobtainable(sdkInt, AllFilesAccessDeclaration.isDeclared(context))

    /** The rule itself, with both inputs supplied - see [requiresAllFilesAccess]. */
    fun isDirectFileAccessUnobtainable(sdkInt: Int, declaresAllFilesAccess: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.R && !declaresAllFilesAccess

    /**
     * S2369: whether shared storage can be read whole RIGHT NOW. Deliberately not the negation of
     * [isDirectFileAccessUnobtainable], which answers "for good": a build that declares the permission
     * but has not been granted it reads through the same narrowed MediaProvider as one that can never
     * ask, and a screen keyed off the declaration alone would stay silent on exactly that install.
     * Below API 30 the runtime read permission still opens the whole volume, so nothing is narrowed.
     *
     * The declaration is asked first and it is cached, so a build that declares nothing - every store
     * flavor - never reaches the AppOps call behind `isExternalStorageManager`.
     */
    fun isAllFilesAccessHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true
        }
        return AllFilesAccessDeclaration.isDeclared(context) && Environment.isExternalStorageManager()
    }

    /**
     * Whether every storage permission this SDK level needs is currently held. The platform check
     * is written against `Build.VERSION_CODES.R` rather than [requiresAllFilesAccess] because
     * `Environment.isExternalStorageManager()` is an API 30 call lint resolves only through the
     * literal comparison.
     *
     * S2012: the all-files reading answers only for a build that declares the permission. Where it
     * is not declared the runtime permissions are the whole of the storage question, and asking
     * `Environment` would report "never granted" for the life of the install.
     */
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && AllFilesAccessDeclaration.isDeclared(context)) {
            return Environment.isExternalStorageManager()
        }
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
