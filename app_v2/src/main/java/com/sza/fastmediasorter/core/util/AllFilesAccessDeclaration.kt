package com.sza.fastmediasorter.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.sza.fastmediasorter.util.getPackageInfoCompat
import timber.log.Timber

/**
 * S2012: whether the merged manifest of THIS build declares `MANAGE_EXTERNAL_STORAGE`.
 *
 * Google Play refused two updates over the permission as `Not a core feature`, so it survives only in
 * `src/noLegal/AndroidManifest.xml`. The answer is read from the package itself rather than from a
 * `BuildConfig` flag: the declaration is the fact the platform acts on, a flavor guard in shared code
 * would only mirror it, and CLAUDE.md Rule 14 forbids one here. Same shape as
 * `DeviceSensorAvailabilityRepositoryImpl.declaresActivityRecognition` (S1614).
 */
object AllFilesAccessDeclaration {

    @Volatile
    private var declared: Boolean? = null

    /** A manifest cannot change while the process lives, so the platform is asked once. */
    fun isDeclared(context: Context): Boolean {
        declared?.let { return it }
        val result = readFromMergedManifest(context)
        declared = result
        Timber.d("AllFilesAccessDeclaration: MANAGE_EXTERNAL_STORAGE declared=%b", result)
        return result
    }

    private fun readFromMergedManifest(context: Context): Boolean = try {
        context.packageManager
            .getPackageInfoCompat(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(Manifest.permission.MANAGE_EXTERNAL_STORAGE) == true
    } catch (e: PackageManager.NameNotFoundException) {
        // The app's own package always resolves; an impossible miss still has to answer something, and
        // "not declared" is the safe half - it routes to SAF instead of to a screen no build can use.
        Timber.w(e, "Own package info unavailable, treating all-files access as undeclared")
        false
    }
}
