package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PermissionHelper

/**
 * Encapsulates the storage permission startup flow: checks current state, asks once per session,
 * persists "asked" flag, and launches the platform-appropriate permission UI (settings on R+,
 * runtime dialog otherwise).
 *
 * Extracted from MainActivity to keep the activity below the 1000-line cap.
 */
class MainStoragePermissionsHelper(
    private val activity: AppCompatActivity,
    private val storagePermissionLauncher: ActivityResultLauncher<Array<String>>,
    private val settingsPermissionLauncher: ActivityResultLauncher<Intent>
) {

    private var permissionCheckDoneThisSession = false

    fun hasFullLocalPermissions(): Boolean = PermissionHelper.checkStoragePermissions(activity)

    fun checkLocalPermissionsOnStartup() {
        if (permissionCheckDoneThisSession) return
        permissionCheckDoneThisSession = true

        if (hasFullLocalPermissions()) return

        if (!wasStoragePermissionRequested()) {
            markStoragePermissionRequested()
        }
        showStoragePermissionRequestDialog()
    }

    private fun wasStoragePermissionRequested(): Boolean =
        activity.getSharedPreferences(PREFS_NAME_APP, Context.MODE_PRIVATE)
            .getBoolean(KEY_STORAGE_PERMISSION_REQUESTED, false)

    private fun markStoragePermissionRequested() {
        activity.getSharedPreferences(PREFS_NAME_APP, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STORAGE_PERMISSION_REQUESTED, true)
            .apply()
    }

    private fun showStoragePermissionRequestDialog() {
        if (activity.isFinishing || activity.isDestroyed) return
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.getString(R.string.permission_storage_rationale_r)
        } else {
            activity.getString(R.string.permission_storage_rationale)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.permissions_required_title)
            .setMessage(message)
            .setPositiveButton(R.string.grant_permissions) { _, _ -> launchStoragePermissionFlow() }
            .setNegativeButton(R.string.continue_anyway, null)
            .setCancelable(true)
            .show()
    }

    private fun launchStoragePermissionFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
            } catch (_: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
            settingsPermissionLauncher.launch(intent)
        } else {
            storagePermissionLauncher.launch(PermissionHelper.getStoragePermissionsArray())
        }
    }

    companion object {
        private const val PREFS_NAME_APP = "app_prefs"
        private const val KEY_STORAGE_PERMISSION_REQUESTED = "storage_permission_requested"
    }
}
