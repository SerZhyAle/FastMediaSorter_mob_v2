package com.sza.fastmediasorter.ui.networkmonitor.helpers

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.ui.common.permissions.canRequestPermission
import com.sza.fastmediasorter.ui.common.permissions.markPermissionRequested
import java.util.WeakHashMap

/** Offers the correct recovery action for a Monitor section that lacks one runtime permission. */
class NetworkMonitorPermissionManager(private val fragment: Fragment) {

    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        bound.clear()
    }

    /**
     * The denial each view was last bound for. The sections call [bind] on every 1 Hz snapshot, and
     * [canRequestPermission] costs two binder calls plus a marker read, so an unchanged denial keeps its
     * text and listener. Weak keys: a view recreated after onDestroyView is a new entry, never a stale hit.
     */
    private val bound = WeakHashMap<TextView, SectionAvailability.NoPermission>()

    init {
        // The grant, and whether the dialog can still be shown, change only outside this screen
        // (system settings, the permission dialog), and both return through onResume.
        fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                bound.clear()
            }
        })
    }

    fun bind(view: TextView, availability: SectionAvailability): Boolean {
        val denied = availability as? SectionAvailability.NoPermission
        if (denied == null) {
            bound.remove(view)
            return false
        }
        // The caller resets visibility from its own state before each bind, so this line is not cached.
        view.isVisible = true
        if (bound.put(view, denied) != denied) {
            applyAction(view, denied)
        }
        return true
    }

    private fun applyAction(view: TextView, denied: SectionAvailability.NoPermission) {
        val activity = fragment.requireActivity()
        val canRequest = activity.canRequestPermission(denied.manifestName)
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
