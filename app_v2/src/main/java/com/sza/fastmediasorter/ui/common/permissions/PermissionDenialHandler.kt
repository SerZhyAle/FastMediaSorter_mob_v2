package com.sza.fastmediasorter.ui.common.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.domain.model.PermissionEntry
import timber.log.Timber

object PermissionDenialHandler {

    fun handle(fragment: Fragment, entry: PermissionEntry) {
        val view = fragment.view ?: return
        Timber.w("Permission permanently denied: %s", entry.manifestName)
        Snackbar.make(view, "To enable ${fragment.getString(entry.titleRes.takeIf { it != 0 } ?: return)}, open App Settings", Snackbar.LENGTH_LONG)
            .setAction("Open Settings") { openAppSettings(fragment.requireActivity()) }
            .show()
    }

    fun handle(activity: Activity, entry: PermissionEntry) {
        Timber.w("Permission permanently denied: %s", entry.manifestName)
        val view = activity.window.decorView.rootView
        Snackbar.make(view, "Permission required. Open App Settings to enable.", Snackbar.LENGTH_LONG)
            .setAction("Open Settings") { openAppSettings(activity) }
            .show()
    }

    private fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
}
