package com.sza.fastmediasorter.ui.settings.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.util.findLifecycleOwner
import timber.log.Timber

/**
 * S2776: owns what the flashlight shade shortcut's settings row needs from the device - the
 * notification permission, and whether there is a flash unit at all.
 *
 * A class of its own rather than members on `OperationsSettingsFragment`: that host sits exactly at
 * detekt's `LargeClass` ceiling, and S2516 split `OperationsProgramsManager` off it for the same
 * reason. For the same reason the host is not asked for a `Fragment` either - the activity is
 * unwrapped from the view's context, the way `LifecycleDialogExt` resolves its own owner, so this row
 * costs that class nothing at all.
 *
 * Registration goes through [androidx.activity.result.ActivityResultRegistry] rather than
 * `Fragment.registerForActivityResult`, which is refused once the fragment has started - and the
 * settings managers are all built lazily, well after that. [viewModelOf] is a lambda so nothing
 * dereferences the host's `by activityViewModels()` before it is attached.
 */
class OperationsFlashlightShortcutPermissionManager(
    private val context: Context,
    private val viewModelOf: () -> SettingsViewModel,
) {

    private val launcher: ActivityResultLauncher<String>? =
        (context.findLifecycleOwner() as? ActivityResultRegistryOwner)
            ?.activityResultRegistry
            ?.register(REGISTRY_KEY, ActivityResultContracts.RequestPermission()) { granted ->
                // Written only on a grant: a denial must leave the row off rather than on and mute.
                if (granted) {
                    val viewModel = viewModelOf()
                    viewModel.updateSettings(
                        viewModel.settings.value.copy(flashlightShortcutNotificationEnabled = true)
                    )
                }
            }

    /** False on a device with no flash, where the row would promise what the hardware cannot do. */
    val isFlashUnitAvailable: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    /** True when the row may be switched on now; false means the request is on screen instead. */
    fun ensureGranted(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // A null launcher means the view's context chain held no activity, which cannot happen
            // from a settings screen; the row then stays off rather than switching on and going mute.
            launcher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                ?: Timber.w("FlashlightShortcutPermission: no activity to ask from, row stays off")
        }
        return granted
    }

    private companion object {
        const val REGISTRY_KEY = "S2776_flashlight_shortcut_post_notifications"
    }
}
