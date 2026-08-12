package com.sza.fastmediasorter.ui.common.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionStatus
import timber.log.Timber

object PermissionDenialHandler {

    fun handle(fragment: Fragment, entry: PermissionEntry, status: PermissionStatus) {
        if (!needsSettingsRoute(status)) return
        val view = fragment.view ?: return
        Timber.w("Permission permanently denied: %s", entry.manifestName)
        val message = entry.titleRes
            .takeIf { it != 0 }
            ?.let { fragment.getString(R.string.permission_open_settings_for_feature, fragment.getString(it)) }
            ?: fragment.getString(R.string.permission_open_settings_generic)
        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.perm_btn_open_system_settings) { openAppSettings(fragment.requireActivity()) }
            .show()
    }

    fun handle(activity: Activity, entry: PermissionEntry, status: PermissionStatus) {
        if (!needsSettingsRoute(status)) return
        Timber.w("Permission permanently denied: %s", entry.manifestName)
        val view = activity.window.decorView.rootView
        val message = entry.titleRes
            .takeIf { it != 0 }
            ?.let { activity.getString(R.string.permission_open_settings_for_feature, activity.getString(it)) }
            ?: activity.getString(R.string.permission_open_settings_generic)
        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.perm_btn_open_system_settings) { openAppSettings(activity) }
            .show()
    }

    /**
     * Only a permanent denial leaves the system settings screen as the sole way to grant a permission.
     * A never-requested or plainly denied one can still be granted from the system dialog, so sending
     * the user to settings would be the long way round.
     */
    private fun needsSettingsRoute(status: PermissionStatus): Boolean = when (status) {
        PermissionStatus.PERMANENTLY_DENIED -> true
        PermissionStatus.NOT_YET_REQUESTED,
        PermissionStatus.DENIED,
        PermissionStatus.GRANTED,
        PermissionStatus.NOT_APPLICABLE,
        // Nothing in system settings to change - the consent dialog comes back on the next use.
        PermissionStatus.ASKED_EACH_TIME -> false
    }

    private fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
}
