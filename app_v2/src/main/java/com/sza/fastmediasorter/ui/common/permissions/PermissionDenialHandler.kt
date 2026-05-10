package com.sza.fastmediasorter.ui.common.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.domain.model.PermissionEntry
import timber.log.Timber

object PermissionDenialHandler {

    fun handle(fragment: Fragment, entry: PermissionEntry) {
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

    fun handle(activity: Activity, entry: PermissionEntry) {
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

    private fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
}
