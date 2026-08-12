package com.sza.fastmediasorter.ui.main.helpers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.compat.ChromeOsCompat
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.core.util.SettingsIntentLauncher
import com.sza.fastmediasorter.core.util.StoragePermissionRule
import com.sza.fastmediasorter.ui.common.permissions.permissionRationale
import com.sza.fastmediasorter.util.showBoundToHost
import timber.log.Timber

/**
 * Encapsulates the storage permission startup flow: checks current state, asks once per session,
 * persists "asked" flag, and launches the platform-appropriate permission UI (settings on R+,
 * runtime dialog otherwise).
 *
 * Extracted from MainActivity to keep the activity below the 1000-line cap.
 */
class MainStoragePermissionsHelper(
    private val activity: AppCompatActivity,
    private val storagePermissionLauncher: ActivityResultLauncher<Array<String>>
) {

    private var permissionCheckDoneThisSession = false

    // Held so the host can close it on destroy: a shown dialog's window otherwise survives a
    // configuration recreate and keeps the dead Activity alive (S1197).
    private var rationaleDialog: AlertDialog? = null

    fun hasFullLocalPermissions(): Boolean = PermissionHelper.checkStoragePermissions(activity)

    fun checkLocalPermissionsOnStartup() {
        if (ChromeOsCompat.isChromeOs(activity)) {
            Timber.d("MainStoragePermissionsHelper: skipping MANAGE_EXTERNAL_STORAGE on Chrome OS")
            return
        }
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
        // onSettingsResult() re-runs the startup check, which would otherwise stack a second
        // rationale on top of the one already on screen.
        if (rationaleDialog?.isShowing == true) return
        // S1436: which permission is being explained follows the one storage rule, and the words come
        // from that permission's registry row - this dialog no longer owns either decision.
        val permission = if (StoragePermissionRule.requiresAllFilesAccess()) {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val message = activity.permissionRationale(permission)
        Timber.d("S1436: storage rationale shown for $permission")
        rationaleDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.permissions_required_title)
            .setMessage(message)
            .setPositiveButton(R.string.grant_permissions) { _, _ -> launchStoragePermissionFlow() }
            .setNegativeButton(R.string.continue_anyway, null)
            .setCancelable(true)
            .setOnDismissListener { rationaleDialog = null }
            .showBoundToHost(activity)
    }

    /**
     * Must be called from the host activity's `onDestroy`. Without it the rationale dialog's window
     * outlives a configuration recreate and leaks the Activity (S1197).
     */
    fun dismissPendingDialog() {
        rationaleDialog?.dismiss()
        rationaleDialog = null
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
            SettingsIntentLauncher.launch(activity, intent, PermissionHelper.REQUEST_CODE_MANAGE_STORAGE)
        } else {
            storagePermissionLauncher.launch(PermissionHelper.getStoragePermissionsArray())
        }
    }

    /**
     * Must be called from the host activity's `onActivityResult` for
     * `PermissionHelper.REQUEST_CODE_MANAGE_STORAGE`. Resets the once-per-session flag and
     * re-runs the startup check so the user is re-evaluated after returning from Settings.
     */
    fun onSettingsResult() {
        permissionCheckDoneThisSession = false
        checkLocalPermissionsOnStartup()
    }

    companion object {
        private const val PREFS_NAME_APP = "app_prefs"
        private const val KEY_STORAGE_PERMISSION_REQUESTED = "storage_permission_requested"
    }
}
