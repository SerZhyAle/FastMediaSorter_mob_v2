package com.sza.fastmediasorter.core.util

import android.Manifest
import android.app.Activity
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
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
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

    /**
     * Request storage permission based on Android version.
     */
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                ),
                REQUEST_CODE_STORAGE
            )
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_CODE_STORAGE
            )
        }
    }

    /**
     * Request MANAGE_MEDIA permission (Android 12+).
     * This opens app settings where user must manually grant permission.
     */
    fun requestManageMediaPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_MEDIA)
            } catch (e: Exception) {
                // Fallback to app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_MEDIA)
            }
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
     * Get permission status message for user.
     */
    fun getStoragePermissionMessage(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getString(com.sza.fastmediasorter.R.string.permission_storage_rationale_r)
        } else {
            context.getString(com.sza.fastmediasorter.R.string.permission_storage_rationale)
        }
    }

    fun getInternetPermissionMessage(context: Context): String {
        return context.getString(com.sza.fastmediasorter.R.string.permission_internet_rationale)
    }

    fun getManageMediaPermissionMessage(context: Context): String {
        return context.getString(com.sza.fastmediasorter.R.string.manage_media_explanation)
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission (Android 11+).
     * Opens system settings where user must manually grant "All files access" permission.
     * Required to select system folders like Pictures, Downloads, DCIM.
     */
    fun requestAllFilesAccessPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_CODE_ALL_FILES_ACCESS)
            } catch (e: Exception) {
                Timber.w(e, "Failed to open app-specific all files access settings, trying general settings")
                // Fallback to general all files access settings
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivityForResult(intent, REQUEST_CODE_ALL_FILES_ACCESS)
                } catch (e2: Exception) {
                    Timber.e(e2, "Failed to open all files access settings")
                    // Last fallback - app details
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", activity.packageName, null)
                    }
                    activity.startActivityForResult(intent, REQUEST_CODE_ALL_FILES_ACCESS)
                }
            }
        }
    }

    fun getAllFilesAccessPermissionMessage(context: Context): String {
        return context.getString(com.sza.fastmediasorter.R.string.all_files_access_explanation)
    }

    /**
     * Unified storage permission check across all Android versions (API 28-35+).
     * 
     * Returns:
     * - `true` if all required storage permissions are granted for current API level
     * - `false` if any permission is missing
     * 
     * API level breakdown:
     * - **API 30+ (Android 11+)**: Checks if `MANAGE_EXTERNAL_STORAGE` is granted via `Environment.isExternalStorageManager()`
     * - **API 29 (Android 10)**: Checks `READ_EXTERNAL_STORAGE` permission. App uses `requestLegacyExternalStorage=true` in manifest.
     * - **API 28 (Android 9)**: Checks both `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` permissions.
     * - **API 23-27 (Android 6-8)**: Checks `READ_EXTERNAL_STORAGE` (fallback for legacy flavor only).
     * 
     * @param context Application or Activity context
     * @return `true` if storage permissions are granted, `false` otherwise
     */
    fun checkStoragePermissions(context: Context): Boolean {
        return when {
            // API 30+ (Android 11+): MANAGE_EXTERNAL_STORAGE required for full access
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val hasManageStorage = Environment.isExternalStorageManager()
                Timber.d("PermissionHelper: API ${Build.VERSION.SDK_INT} - MANAGE_EXTERNAL_STORAGE=$hasManageStorage")
                hasManageStorage
            }
            
            // API 29 (Android 10): Use READ_EXTERNAL_STORAGE + legacy storage flag
            // Legacy storage flag is set in AndroidManifest: android:requestLegacyExternalStorage="true"
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                val hasReadStorage = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                Timber.d("PermissionHelper: API ${Build.VERSION.SDK_INT} - READ_EXTERNAL_STORAGE=$hasReadStorage (legacy storage enabled)")
                hasReadStorage
            }
            
            // API 28 (Android 9): Standard READ + WRITE permissions
            Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> {
                val hasRead = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                val hasWrite = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                Timber.d("PermissionHelper: API ${Build.VERSION.SDK_INT} - READ=$hasRead, WRITE=$hasWrite")
                hasRead && hasWrite
            }
            
            // API 23-27 (Android 6-8): Simple READ_EXTERNAL_STORAGE check (legacy flavor only)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val hasReadStorage = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                Timber.d("PermissionHelper: API ${Build.VERSION.SDK_INT} - READ_EXTERNAL_STORAGE=$hasReadStorage (legacy)")
                hasReadStorage
            }
            
            // API < 23: Permissions granted at install time
            else -> {
                Timber.d("PermissionHelper: API ${Build.VERSION.SDK_INT} - permissions granted at install time")
                true
            }
        }
    }

    /**
     * Route user to appropriate settings page to grant storage permissions.
     * 
     * Opens the correct system settings page based on Android version:
     * - **API 30+ (Android 11+)**: Opens "All files access" settings page
     * - **API 29 (Android 10)**: Opens "Storage and cache" settings (enable permission + SAF fallback if needed)
     * - **API 28 (Android 9)**: Opens app details settings for runtime permission
     * - **API 23-27 (Android 6-8)**: Opens app details settings for runtime permission
     * 
     * @param activity Active Activity to start settings intent
     */
    fun routeToStorageSettings(activity: Activity) {
        when {
            // API 30+ (Android 11+): Open "All files access" permission page
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Timber.i("PermissionHelper: Routing to MANAGE_ALL_FILES_ACCESS_PERMISSION (API ${Build.VERSION.SDK_INT})")
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to open app-specific all files access settings, using fallback")
                    // Fallback: general all files access settings
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                }
            }
            
            // API 29 (Android 10): Open app settings (Storage section)
            // User should see "Storage" entry → "Clear storage" and permission toggles
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                Timber.i("PermissionHelper: Routing to app settings (API ${Build.VERSION.SDK_INT})")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivityForResult(intent, REQUEST_CODE_STORAGE)
            }
            
            // API 28 (Android 9) and below: Open app settings for runtime permissions
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                Timber.i("PermissionHelper: Routing to app settings (API ${Build.VERSION.SDK_INT})")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivityForResult(intent, REQUEST_CODE_STORAGE)
            }
            
            // API 23-27 (Android 6-8): Open app settings for runtime permissions
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                Timber.i("PermissionHelper: Routing to app settings (API ${Build.VERSION.SDK_INT})")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivityForResult(intent, REQUEST_CODE_STORAGE)
            }
            
            else -> {
                Timber.w("PermissionHelper: No settings routing needed for API ${Build.VERSION.SDK_INT}")
            }
        }
    }
}
