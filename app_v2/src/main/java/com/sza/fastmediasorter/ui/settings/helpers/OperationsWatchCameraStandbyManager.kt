package com.sza.fastmediasorter.ui.settings.helpers

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.ArmWatchCameraStandbyUseCase
import com.sza.fastmediasorter.broadcast.WatchCameraStandby
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S2551: owns the "let my watch see my camera" row of the Wear group - the owner's standing answer to
 * a camera request from his watch, and the capture session that answer stands on.
 *
 * A class of its own rather than members on [OperationsWearGroupManager] or on the host fragment: the
 * host sits at detekt's `LargeClass` ceiling with four lines to spare - measured 2026-09-20, 596 of
 * 600 - and the group manager at its parameter ceiling, which is how
 * [OperationsFlashlightShortcutPermissionManager] came to exist beside it. The two dependencies come
 * through an entry point for the same reason: passing them down would have cost the host exactly the
 * four lines it has left. Registration goes the same way as that manager's - through the activity's
 * result registry, because the settings managers are built lazily, long after
 * `Fragment.registerForActivityResult` stops being allowed.
 */
class OperationsWatchCameraStandbyManager(
    private val binding: FragmentSettingsDestinationsBinding,
    private val fragment: Fragment,
    private val viewModel: SettingsViewModel,
    private val isUpdatingFromSettings: () -> Boolean,
) {

    private val graph: StandbyEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            fragment.requireContext().applicationContext,
            StandbyEntryPoint::class.java,
        )
    }

    private val capabilityAvailability: CapabilityAvailability get() = graph.capabilityAvailability()

    private val armWatchCameraStandby: ArmWatchCameraStandbyUseCase get() = graph.armWatchCameraStandby()

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        fragment.requireActivity().activityResultRegistry.register(
            REGISTRY_KEY,
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            if (hasView()) onPermissionResult(grants[Manifest.permission.CAMERA] == true)
        }

    /**
     * Whether this build answers the watch from a standing arrangement at all.
     *
     * `standard` prompts the owner per request, so the row is absent there rather than switched off - a
     * switch that grants nothing would be a second, contradictory answer to "may my watch see my camera".
     */
    val isAvailableInBuild: Boolean get() = capabilityAvailability.isWatchCameraStandbyAvailable()

    fun setup() {
        binding.rowWatchCameraStandby.isVisible = isAvailableInBuild
        if (!isAvailableInBuild) return
        binding.rowWatchCameraStandby.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            onToggled(isChecked)
        }
        // The row states the live session, not the stored wish: a capture that ended on its own - a
        // reboot, a stop from the watch, a failed camera - must not leave a switch promising a picture
        // nobody is serving. The stored value is the owner's standing answer and is what the switch
        // writes, never what it reads.
        fragment.collectOnLifecycle(armWatchCameraStandby.armed) { isArmed ->
            if (binding.rowWatchCameraStandby.isChecked != isArmed) {
                binding.rowWatchCameraStandby.setCheckedSilently(isArmed)
            }
        }
    }

    fun render(settings: AppSettings) {
        if (!isAvailableInBuild) return
        // The arrangement means nothing while the companion itself is off, so the row follows that
        // switch the way the paired-watch status line above it does.
        binding.rowWatchCameraStandby.isVisible = settings.enableWearCompanion
    }

    private fun onToggled(enabled: Boolean) {
        Timber.d("S2551: standby row toggled to %s", enabled)
        persist(enabled)
        when {
            !enabled -> armWatchCameraStandby.disarm()
            isCameraGranted() -> arm()
            else -> permissionLauncher.launch(STANDBY_PERMISSIONS)
        }
    }

    private fun onPermissionResult(granted: Boolean) {
        if (granted) {
            arm()
        } else {
            revert(fragment.getString(R.string.broadcast_permission_camera_required))
        }
    }

    /**
     * The capture is raised here, while the app is on screen, because the platform refuses to create a
     * camera foreground service for an invisible app and grants no exemption a watch message could
     * reach (research/05). What the watch's request meets later is a session that already exists.
     */
    private fun arm() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val outcome = armWatchCameraStandby.arm()
            if (outcome is WatchCameraStandby.Refused) {
                Timber.w("WatchCameraStandby: arming refused with %s", outcome.refusal)
                revert(fragment.getString(R.string.settings_watch_camera_standby_failed))
            }
        }
    }

    /**
     * Puts the switch back and says why.
     *
     * A checked row over a session that was never armed is the one thing this row must never show: the
     * watch would be refused with nothing on the phone explaining it.
     */
    private fun revert(message: String) {
        persist(false)
        binding.rowWatchCameraStandby.setCheckedSilently(false)
        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /** A result that arrives after the view is gone has no row left to answer for. */
    private fun hasView(): Boolean = fragment.view != null

    private fun persist(enabled: Boolean) {
        val current = viewModel.settings.value
        viewModel.updateSettings(
            current.copy(broadcast = current.broadcast.copy(watchCameraStandby = enabled))
        )
    }

    private fun isCameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            fragment.requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    /** What this manager needs from the graph; see the class KDoc for why it is not passed in. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StandbyEntryPoint {
        fun capabilityAvailability(): CapabilityAvailability
        fun armWatchCameraStandby(): ArmWatchCameraStandbyUseCase
    }

    private companion object {

        const val REGISTRY_KEY = "S2551_watch_camera_standby_permissions"

        /**
         * The microphone rides along because a session armed without it serves a silent picture, and
         * asking once spares the owner a second dialog when he first opens the stream on the watch.
         */
        val STANDBY_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
}
