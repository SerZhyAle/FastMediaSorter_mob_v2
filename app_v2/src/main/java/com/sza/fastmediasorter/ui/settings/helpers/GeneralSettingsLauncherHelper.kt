package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Intent
import android.graphics.Rect
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.ui.settings.LauncherSettingsDialogFragment
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1088: owns the System-launcher entry in General -> Interface: the enable toggle (reflects the HOME
 * component state, launches the system role request) plus the row that opens
 * [LauncherSettingsDialogFragment] for the launcher's own settings. The whole pair is hidden when the
 * build has no launcher surface ([LauncherModeContract.isAvailableInBuild]).
 *
 * S1107: this General screen also receives the onboarding "use as home" deep-link and issues the HOME-role
 * request itself (the finishing Welcome frame cannot). The request is deferred past the first-run
 * recreation storm so it fires from a storm-surviving instance - see [handleLauncherRoleDeepLink].
 */
class GeneralSettingsLauncherHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
    private val launcherModeContract: LauncherModeContract,
    private val launcherRoleManager: LauncherRoleManager,
    private val launcherRoleLauncher: ActivityResultLauncher<Intent>,
) {

    // Guards against scheduling the deferred role request more than once per Activity instance
    // (onResume can fire repeatedly). Reset naturally: each recreated instance gets a fresh helper.
    private var roleRequestScheduled = false

    fun setup() {
        if (!launcherModeContract.isAvailableInBuild) {
            binding.rowLauncherModeEnabled.isVisible = false
            binding.rowLauncherSettings.isVisible = false
            return
        }
        binding.rowLauncherModeEnabled.setOnCheckedChangeListener { isChecked ->
            val host = fragment.activity ?: return@setOnCheckedChangeListener
            if (isChecked) {
                launcherRoleManager.enableMode(host, launcherRoleLauncher)
            } else {
                launcherRoleManager.disableMode()
            }
            updateOpenRowEnabled(isChecked)
        }
        binding.rowLauncherSettings.setOnRowClickListener {
            LauncherSettingsDialogFragment().show(fragment.childFragmentManager, LauncherSettingsDialogFragment.TAG)
        }
        refreshState()
    }

    /** Re-reads the HOME component state; call from onResume and the role-request result callback. */
    fun refreshState() {
        if (!launcherModeContract.isAvailableInBuild) return
        val enabled = launcherRoleManager.isModeEnabled()
        binding.rowLauncherModeEnabled.setCheckedSilently(enabled)
        updateOpenRowEnabled(enabled)
    }

    // The launcher-settings row only makes sense once the launcher is enabled - keep it inert otherwise.
    private fun updateOpenRowEnabled(enabled: Boolean) {
        binding.rowLauncherSettings.isEnabled = enabled
    }

    /**
     * S1107: onboarding opt-in ("use as home screen") routes here from the finishing Welcome frame via the
     * first-run Settings deep-link. Completing onboarding rebuilds MainActivity+SettingsActivity while
     * first-run theme/locale re-application recreates this Activity several times (onCreate/onDestroy
     * storm). Firing enableMode() from a doomed instance makes the system RequestRoleActivity resolve a
     * null caller - that instance is torn down ~86ms after launch, before the caller package is read - so
     * the HOME-role dialog never presents (device-verified 2026-07-18).
     *
     * Fix: defer the request past the storm on the view lifecycle. A destroyed instance cancels its
     * viewLifecycleScope, so every doomed instance's pending request is dropped and only the settled
     * survivor fires - its Activity outlives the caller resolution. The intent extra is consumed only when
     * the request actually fires, so recreated instances keep re-arming until one wins (and it never
     * re-fires afterwards). Consumed-once on the surviving instance also covers later rotations.
     */
    fun handleLauncherRoleDeepLink() {
        if (!launcherModeContract.isAvailableInBuild) return
        if (roleRequestScheduled) return
        val host = fragment.activity ?: return
        if (!host.intent.getBooleanExtra(SettingsActivity.EXTRA_REQUEST_LAUNCHER_ROLE, false)) return
        roleRequestScheduled = true
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            delay(STORM_SETTLE_DELAY_MS)
            val settledHost = fragment.activity ?: return@launch
            if (settledHost.isFinishing || settledHost.isDestroyed) return@launch
            Timber.d("S1107: deferred HOME-role request firing from settled Settings instance")
            settledHost.intent.removeExtra(SettingsActivity.EXTRA_REQUEST_LAUNCHER_ROLE)
            launcherRoleManager.enableMode(settledHost, launcherRoleLauncher)
            revealEnableToggle()
        }
    }

    // Best-effort: expand the (default-collapsed) Interface section and scroll the enable toggle into view
    // so the returning user sees the control the deep-link acted on.
    private fun revealEnableToggle() {
        binding.headerInterface.setExpanded(true, notify = true)
        val target = binding.rowLauncherModeEnabled
        target.post {
            target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), false)
        }
    }

    private companion object {
        // Long enough that a to-be-destroyed instance is torn down (cancelling its request) before
        // firing, yet imperceptible for the one-shot onboarding hand-off. The observed storm destroyed
        // the launching instance ~86ms after launch; this leaves ample margin on slower devices.
        const val STORM_SETTLE_DELAY_MS = 600L
    }
}
