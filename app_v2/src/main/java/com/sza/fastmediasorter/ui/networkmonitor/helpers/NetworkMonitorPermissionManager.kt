package com.sza.fastmediasorter.ui.networkmonitor.helpers

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.ui.common.permissions.canRequestPermission
import com.sza.fastmediasorter.ui.common.permissions.markPermissionRequested

/** Offers the correct recovery action for a Monitor section that lacks one runtime permission. */
class NetworkMonitorPermissionManager(private val fragment: Fragment) {

    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun bind(view: TextView, availability: SectionAvailability): Boolean {
        val denied = availability as? SectionAvailability.NoPermission ?: return false
        val activity = fragment.requireActivity()
        val canRequest = activity.canRequestPermission(denied.manifestName)
        view.isVisible = true
        view.isClickable = true
        view.isFocusable = true
        view.setText(
            if (canRequest) {
                R.string.network_monitor_permission_grant
            } else {
                R.string.network_monitor_permission_open_settings
            }
        )
        view.setOnClickListener {
            if (activity.canRequestPermission(denied.manifestName)) {
                activity.markPermissionRequested(denied.manifestName)
                launcher.launch(denied.manifestName)
            } else {
                openAppSettings()
            }
        }
        return true
    }

    private fun openAppSettings() {
        val context = fragment.requireContext()
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        fragment.startActivity(intent)
    }
}
