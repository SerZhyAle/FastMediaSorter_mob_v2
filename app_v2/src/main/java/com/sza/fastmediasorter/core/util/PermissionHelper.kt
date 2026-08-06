package com.sza.fastmediasorter.core.util

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Helper class for handling runtime permissions.
 */
object PermissionHelper {

    const val REQUEST_CODE_STORAGE = 100
    const val REQUEST_CODE_INTERNET = 101
    const val REQUEST_CODE_MANAGE_STORAGE = 102
    const val REQUEST_CODE_MANAGE_MEDIA = 103
    const val REQUEST_CODE_ALL_FILES_ACCESS = 104

    // String literal used intentionally: Manifest.permission.ACCESS_LOCAL_NETWORK is only
    // available in compileSdk 37+. Keeping compileSdk at 35 until the separate SDK-uplift ticket
    // lands; this guard lets S0035 ship without pulling in the SDK bump prematurely.
    const val LOCAL_NETWORK_API = 37
    const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /**
     * Check if MANAGE_MEDIA permission is granted (Android 12+ / API 31+).
     * This permission allows apps to modify/delete media files without user confirmation dialogs.
     * Essential for fast media sorting workflow.
     */
    fun hasManageMediaPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false // Not available on older versions
        }
        return try {
            // MANAGE_MEDIA is a special permission that must be checked via MediaStore
            android.provider.MediaStore.canManageMedia(context)
        } catch (e: Exception) {
            Timber.e(e, "Error checking MANAGE_MEDIA permission")
            false
        }
    }

    /**
     * Check if MANAGE_EXTERNAL_STORAGE is granted (Android 11+).
     * Required for full file system access and selecting system media folders like Pictures, Downloads.
     * Essential for file manager apps to access any folder on device.
     */
    fun hasAllFilesAccessPermission(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Not needed on older versions
        }
    }

    /**
     * Check if storage permission is granted.
     * For Android 11+ (API 30+), check MANAGE_EXTERNAL_STORAGE.
     * For Android 10 (API 29), use legacy storage BUT still require READ_EXTERNAL_STORAGE at runtime.
     * For Android 6-9 (API 23-28), check READ_EXTERNAL_STORAGE.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return checkStoragePermissions(context)
    }

    /**
     * Check if internet permission is granted.
     * Note: INTERNET permission is normal permission, always granted.
     * But we check it for consistency.
     */
    fun hasInternetPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** S0766: true when fine OR coarse location is granted; callers silently skip geotagging otherwise. */
    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Request storage permission: an all-files-access route where the platform has one, the runtime
     * permissions [StoragePermissionRule] names otherwise.
     */
    fun requestStoragePermission(activity: Activity) {
        if (StoragePermissionRule.requiresAllFilesAccess()) {
            routeToStorageSettings(activity)
            return
        }
        val permissions = StoragePermissionRule.requiredPermissions()
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE_STORAGE)
        }
    }

    /**
     * Request MANAGE_MEDIA permission (Android 12+).
     * This opens app settings where user must manually grant permission.
     */
    fun requestManageMediaPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            SettingsIntentLauncher.launch(activity, intent, REQUEST_CODE_MANAGE_MEDIA)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No MANAGE_MEDIA screen on this device, falling back to app details")
            launchAppDetailsSettings(activity, REQUEST_CODE_MANAGE_MEDIA)
        }
    }

    /**
     * Check if should show rationale for storage permission.
     */
    fun shouldShowStorageRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            false
        } else {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission (Android 11+).
     * Opens system settings where user must manually grant "All files access" permission.
     * Required to select system folders like Pictures, Downloads, DCIM.
     */
    fun requestAllFilesAccessPermission(activity: Activity) {
        if (StoragePermissionRule.requiresAllFilesAccess()) {
            launchAllFilesAccessSettings(activity, REQUEST_CODE_ALL_FILES_ACCESS)
        }
    }

    /**
     * The one owner of the all-files-access route: the package-scoped screen, then the global list,
     * then app details. [SettingsIntentLauncher] lets `ActivityNotFoundException` through, so each
     * step is a reachable fallback rather than decoration.
     */
    private fun launchAllFilesAccessSettings(activity: Activity, requestCode: Int) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            SettingsIntentLauncher.launch(activity, intent, requestCode)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No package-scoped all files access screen, trying the global list")
            try {
                SettingsIntentLauncher.launch(
                    activity,
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                    requestCode,
                )
            } catch (e2: ActivityNotFoundException) {
                Timber.e(e2, "No all files access screen at all, falling back to app details")
                launchAppDetailsSettings(activity, requestCode)
            }
        }
    }

    private fun launchAppDetailsSettings(activity: Activity, requestCode: Int) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        SettingsIntentLauncher.launch(activity, intent, requestCode)
    }

    /**
     * Whether every storage permission this SDK level needs is currently held. The SDK branching
     * belongs to [StoragePermissionRule]; this stays as the name the UI layer already calls.
     */
    fun checkStoragePermissions(context: Context): Boolean {
        val granted = StoragePermissionRule.isGranted(context)
        Timber.d("PermissionHelper: API ${Build.VERSION.SDK_INT} - storage granted=$granted")
        return granted
    }

    /**
     * The storage permissions to hand to a runtime request on this SDK level.
     */
    fun getStoragePermissionsArray(): Array<String> = StoragePermissionRule.requiredPermissions()

    /**
     * Route the user to the settings page that can grant storage access: the all-files-access
     * screen where the platform has one, the app details page otherwise - below API 30 every
     * storage permission is a runtime one, and app details is the only screen that re-grants a
     * denied runtime permission.
     *
     * @param activity Active Activity to start settings intent
     */
    fun routeToStorageSettings(activity: Activity) {
        if (StoragePermissionRule.requiresAllFilesAccess()) {
            Timber.i("PermissionHelper: routing to all files access (API ${Build.VERSION.SDK_INT})")
            launchAllFilesAccessSettings(activity, REQUEST_CODE_MANAGE_STORAGE)
            return
        }
        Timber.i("PermissionHelper: routing to app settings (API ${Build.VERSION.SDK_INT})")
        launchAppDetailsSettings(activity, REQUEST_CODE_STORAGE)
    }

    const val REQUEST_CODE_LOCAL_NETWORK = 105

    fun isLocalNetworkRuntimePermissionExpected(): Boolean =
        Build.VERSION.SDK_INT >= LOCAL_NETWORK_API

    fun hasLocalNetworkPermission(context: Context): Boolean {
        if (!isLocalNetworkRuntimePermissionExpected()) return true
        return ContextCompat.checkSelfPermission(
            context,
            LOCAL_NETWORK_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun shouldShowLocalNetworkRationale(activity: Activity): Boolean {
        if (!isLocalNetworkRuntimePermissionExpected()) return false
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            LOCAL_NETWORK_PERMISSION
        )
    }

    fun requestLocalNetworkPermission(activity: Activity) {
        if (!isLocalNetworkRuntimePermissionExpected()) return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(LOCAL_NETWORK_PERMISSION),
            REQUEST_CODE_LOCAL_NETWORK
        )
    }

    fun routeToLocalNetworkSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        SettingsIntentLauncher.launch(activity, intent, REQUEST_CODE_LOCAL_NETWORK)
    }

    /**
     * Handle the ACCESS_LOCAL_NETWORK runtime request result. When the user denied permanently
     * (system will no longer surface the grant dialog), route to the app settings page so the
     * rationale "Open settings" action stays actionable instead of dead-ending on a silent
     * re-request. Returns true when the permission ended up granted.
     */
    fun onLocalNetworkPermissionResult(activity: Activity, grantResults: IntArray): Boolean {
        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!granted && !shouldShowLocalNetworkRationale(activity)) {
            routeToLocalNetworkSettings(activity)
        }
        return granted
    }
}
