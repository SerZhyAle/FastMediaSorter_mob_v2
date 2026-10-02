package com.sza.fastmediasorter.ui.tourist.helpers

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityTouristInfoBinding
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.ui.common.permissions.canRequestPermission
import com.sza.fastmediasorter.ui.common.permissions.markPermissionRequested
import timber.log.Timber

/**
 * Explains the first grant the Tourist dashboard is missing and offers its system request, or the app
 * settings once the platform will no longer show that request.
 *
 * The activity owns the result launcher: it must be registered before the activity starts, and this
 * manager is created later, in the deferred setupViews.
 */
class TouristPermissionManager(
    private val activity: ComponentActivity,
    private val binding: ActivityTouristInfoBinding,
    private val launchRequest: (Array<String>) -> Unit,
) {

    /** The permission the card was last bound for, so a 1 Hz telemetry re-bind skips the binder calls. */
    private var boundPermission: String? = null

    fun bind(state: TouristDashboardState) {
        val missing = missingPermission(state)
        binding.cardPermission.isVisible = missing != null
        if (missing == null) {
            boundPermission = null
            return
        }
        if (missing == boundPermission) return
        boundPermission = missing
        applyAction(missing)
    }

    /** Whether the request can still be shown changes only outside this screen, and comes back through onResume. */
    fun invalidate() {
        boundPermission = null
    }

    private fun missingPermission(state: TouristDashboardState): String? = when {
        !state.hasLocationPermission -> Manifest.permission.ACCESS_FINE_LOCATION
        // stepsAvailable already implies the build declares the permission (S1614), so a build without it
        // never offers a request the system would refuse outright.
        state.stepsAvailable && !state.hasActivityRecognitionPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Manifest.permission.ACTIVITY_RECOGNITION
        else -> null
    }

    private fun applyAction(permission: String) {
        Timber.d("S4069: permission card shown for $permission")
        binding.tvPermissionMessage.setText(
            if (permission == Manifest.permission.ACCESS_FINE_LOCATION) {
                R.string.tourist_permission_location_message
            } else {
                R.string.tourist_permission_steps_message
            }
        )
        binding.btnPermissionAction.setText(
            if (activity.canRequestPermission(permission)) {
                R.string.tourist_permission_grant
            } else {
                R.string.tourist_permission_open_settings
            }
        )
        binding.btnPermissionAction.setOnClickListener {
            if (activity.canRequestPermission(permission)) {
                val request = requestSet(permission)
                request.forEach { activity.markPermissionRequested(it) }
                launchRequest(request)
            } else {
                openAppSettings()
            }
        }
    }

    /**
     * Location is asked as the fine and coarse pair: since API 31 the dialog offers "approximate", and a
     * fine-only request would leave the user no way to grant that choice.
     */
    private fun requestSet(permission: String): Array<String> =
        if (permission == Manifest.permission.ACCESS_FINE_LOCATION) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            arrayOf(permission)
        }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null),
        )
        activity.startActivity(intent)
    }
}
