package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/**
 * S2381: tests for [GeneralSettingsLauncherHelper] launcher toggle state synchronization.
 */
class GeneralSettingsLauncherHelperTest {

    private val binding = mockk<FragmentSettingsGeneralBinding>(relaxed = true)
    private val rowLauncherModeEnabled = mockk<SettingsToggleRow>(relaxed = true)
    private val rowLauncherSettings = mockk<MaterialButton>(relaxed = true)
    private val fragment = mockk<Fragment>(relaxed = true)
    private val launcherModeContract = mockk<LauncherModeContract>()
    private val launcherRoleManager = mockk<LauncherRoleManager>(relaxed = true)
    private val launcherRoleLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)

    private lateinit var helper: GeneralSettingsLauncherHelper

    @Before
    fun setUp() {
        every { binding.rowLauncherModeEnabled } returns rowLauncherModeEnabled
        every { binding.rowLauncherSettings } returns rowLauncherSettings
        every { launcherModeContract.isAvailableInBuild } returns true

        helper = GeneralSettingsLauncherHelper(
            binding = binding,
            fragment = fragment,
            launcherModeContract = launcherModeContract,
            launcherRoleManager = launcherRoleManager,
            launcherRoleLauncher = launcherRoleLauncher,
        )
    }

    @Test
    fun `refreshState does nothing when launcher is not available in build`() {
        every { launcherModeContract.isAvailableInBuild } returns false

        helper.refreshState()

        verify(exactly = 0) { launcherRoleManager.isRoleRequestPending() }
        verify(exactly = 0) { launcherRoleManager.isHomeRoleHeld() }
        verify(exactly = 0) { rowLauncherModeEnabled.setCheckedSilently(any()) }
    }

    @Test
    fun `refreshState does not alter toggle when role request is pending`() {
        every { launcherRoleManager.isRoleRequestPending() } returns true

        helper.refreshState()

        verify(exactly = 0) { launcherRoleManager.isHomeRoleHeld() }
        verify(exactly = 0) { rowLauncherModeEnabled.setCheckedSilently(any()) }
    }

    @Test
    fun `refreshState sets toggle enabled when role is held`() {
        every { launcherRoleManager.isRoleRequestPending() } returns false
        every { launcherRoleManager.isHomeRoleHeld() } returns true

        helper.refreshState()

        verify { rowLauncherModeEnabled.setCheckedSilently(true) }
        verify { rowLauncherSettings.isEnabled = true }
        verify(exactly = 0) { launcherRoleManager.disableMode() }
    }

    @Test
    fun `refreshState disables mode and turns toggle off when role is not held and mode was enabled`() {
        every { launcherRoleManager.isRoleRequestPending() } returns false
        every { launcherRoleManager.isHomeRoleHeld() } returns false
        every { launcherRoleManager.isModeEnabled() } returns true

        helper.refreshState()

        verify(exactly = 1) { launcherRoleManager.disableMode() }
        verify { rowLauncherModeEnabled.setCheckedSilently(false) }
        verify { rowLauncherSettings.isEnabled = false }
    }

    @Test
    fun `refreshState sets toggle off when role is not held and mode was disabled`() {
        every { launcherRoleManager.isRoleRequestPending() } returns false
        every { launcherRoleManager.isHomeRoleHeld() } returns false
        every { launcherRoleManager.isModeEnabled() } returns false

        helper.refreshState()

        verify(exactly = 0) { launcherRoleManager.disableMode() }
        verify { rowLauncherModeEnabled.setCheckedSilently(false) }
        verify { rowLauncherSettings.isEnabled = false }
    }
}