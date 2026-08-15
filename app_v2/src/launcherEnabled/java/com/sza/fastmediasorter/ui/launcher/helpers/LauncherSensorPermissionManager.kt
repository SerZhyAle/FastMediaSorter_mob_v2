package com.sza.fastmediasorter.ui.launcher.helpers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import timber.log.Timber

/**
 * S1179: which sensor gadget needs which runtime permission, and the single place that asks.
 *
 * The whole decision lives here rather than in `LauncherHomeActivity` (Rule 3), and every later
 * sensor gadget adds a row to [PERMISSIONS] instead of a second manager.
 *
 * Built in an Activity field initialiser, like `LauncherContactPickManager` beside it: that is the
 * only point early enough to register an activity-result contract, since one registered after the
 * Activity is STARTED throws.
 */
class LauncherSensorPermissionManager(private val activity: FragmentActivity) {

    /**
     * The placement waiting on the user's answer. One flow runs at a time and it is modal, so there
     * is nothing to reconcile - the same reasoning `LauncherContactPickManager` records for its own
     * in-flight pick.
     */
    private var pendingPlacement: (() -> Unit)? = null

    private val request = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val pending = pendingPlacement
        pendingPlacement = null
        pending?.invoke()
    }

    /**
     * Asks at the moment the user adds the gadget that needs it, which is what strategic §3.3
     * requires instead of a prompt at startup, and places the cell once they answer. A refusal still
     * places it - the answer decides what the tile shows, never whether it appears (§11.5).
     *
     * [place] runs after the answer rather than before it because that is the only thing that makes
     * a fresh grant visible: the desktop binder rebuilds cell views only when the cell list changes
     * (`LauncherCellViewBinder.bind`'s `lastBound` guard, deliberate since S1173/S1288), so a tile
     * placed before the dialog would keep reading the pre-answer permission until some unrelated
     * edit rebuilt the desktop.
     */
    fun placeAfterAsking(gadgetKey: String, place: () -> Unit) {
        val permission = PERMISSIONS[gadgetKey]?.takeIf { isRequestable(it) }
        if (permission == null || isGranted(permission)) {
            place()
            return
        }
        pendingPlacement = place
        request.launch(permission)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * `ACTIVITY_RECOGNITION` only became a runtime permission at API 29, so asking below it would be
     * a dialog the system never shows. Unreachable today - `StepsGadget.isAvailable()` already hides
     * the tile below 29 - but that is a rule in another class, and this one must not depend on it
     * silently.
     */
    private fun isRequestable(permission: String): Boolean =
        permission != Manifest.permission.ACTIVITY_RECOGNITION ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private companion object {
        /**
         * The compass tile needs the location grant too - not for the heading, which no permission
         * gates, but for the altitude line it shares with the speed tile (strategic §3.1.1).
         */
        val PERMISSIONS = mapOf(
            LauncherGadgetRegistry.KEY_COMPASS to Manifest.permission.ACCESS_FINE_LOCATION,
            LauncherGadgetRegistry.KEY_SPEED to Manifest.permission.ACCESS_FINE_LOCATION,
            LauncherGadgetRegistry.KEY_STEPS to Manifest.permission.ACTIVITY_RECOGNITION,
            // S1175: the map tile asks here and nowhere else - strategic §3.2 forbids requesting
            // location at startup, and a refusal still places the cell in its no-permission state.
            LauncherGadgetRegistry.KEY_MAP to Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}
