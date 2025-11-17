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

/**
 * Helper class for handling runtime permissions.
 */
object PermissionHelper {

    const val REQUEST_CODE_STORAGE = 100
    const val REQUEST_CODE_INTERNET = 101
    const val REQUEST_CODE_MANAGE_STORAGE = 102

    /**
     * Check if storage permission is granted.
     * For Android 11+ (API 30+), check MANAGE_EXTERNAL_STORAGE.
     * For Android 10 (API 29), use legacy storage.
     * For Android 6-9 (API 23-28), check READ_EXTERNAL_STORAGE.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 - use legacy storage
            true
        } else {
            // Android 6-9
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - request MANAGE_EXTERNAL_STORAGE
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 - request READ_EXTERNAL_STORAGE
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
}
