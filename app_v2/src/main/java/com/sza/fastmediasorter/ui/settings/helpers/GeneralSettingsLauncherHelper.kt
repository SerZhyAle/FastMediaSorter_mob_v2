package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Intent
import android.graphics.Rect
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.ui.settings.LauncherSettingsDialogFragment
import com.sza.fastmediasorter.ui.settings.SettingsActivity

/**
 * S1088: owns the System-launcher entry in General -> Interface: the enable toggle (reflects the HOME
 * component state, launches the system role request) plus the row that opens
 * [LauncherSettingsDialogFragment] for the launcher's own settings. The whole pair is hidden when the
 * build has no launcher surface ([LauncherModeContract.isAvailableInBuild]).
 *
 * S1107: this General screen also receives the onboarding "use as home" deep-link. It does not finish
 * (unlike the Welcome frame), so the role request fires reliably here - see [handleLauncherRoleDeepLink].
 */
class GeneralSettingsLauncherHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
    private val launcherModeContract: LauncherModeContract,
    private val launcherRoleManager: LauncherRoleManager,
    private val launcherRoleLauncher: ActivityResultLauncher<Intent>,
) {

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
     * S1107: onboarding opt-in ("use as home screen") routes here from the finishing Welcome frame. This
     * Settings screen does not finish, so the working enableMode() role request is reliable here - unlike
     * ADR-2's "chooser on next Home press", which never fires when a default launcher is already set
     * (every real device). Consumed once so it does not re-fire on rotation.
     */
    fun handleLauncherRoleDeepLink() {
        if (!launcherModeContract.isAvailableInBuild) return
        val host = fragment.activity ?: return
        val intent = host.intent
        if (intent.getBooleanExtra(SettingsActivity.EXTRA_REQUEST_LAUNCHER_ROLE, false)) {
            intent.removeExtra(SettingsActivity.EXTRA_REQUEST_LAUNCHER_ROLE)
            launcherRoleManager.enableMode(host, launcherRoleLauncher)
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
}
