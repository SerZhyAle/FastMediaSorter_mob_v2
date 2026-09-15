package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Intent
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.core.launcher.LauncherStartWindowManager
import com.sza.fastmediasorter.core.util.XrDeviceProbe
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.testing.MainDispatcherRule
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * S2381: tests for [GeneralSettingsLauncherHelper] launcher toggle state synchronization.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeneralSettingsLauncherHelperTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val binding = mockk<FragmentSettingsGeneralBinding>(relaxed = true)
    private val rowLauncherModeEnabled = mockk<SettingsToggleRow>(relaxed = true)
    private val rowLauncherStartWindow = mockk<SettingsToggleRow>(relaxed = true)
    private val rowLauncherSettings = mockk<MaterialButton>(relaxed = true)
    private val fragment = mockk<Fragment>(relaxed = true)
    private val launcherModeContract = mockk<LauncherModeContract>()
    private val launcherRoleManager = mockk<LauncherRoleManager>(relaxed = true)
    private val launcherStartWindowManager = mockk<LauncherStartWindowManager>(relaxed = true)
    private val launcherRoleLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)

    private lateinit var helper: GeneralSettingsLauncherHelper

    @Before
    fun setUp() {
        setBindingField("rowLauncherModeEnabled", rowLauncherModeEnabled)
        setBindingField("rowLauncherStartWindow", rowLauncherStartWindow)
        setBindingField("rowLauncherSettings", rowLauncherSettings)
        every { launcherModeContract.isAvailableInBuild } returns true
        every { launcherStartWindowManager.isEnabled() } returns true
        mockkObject(XrDeviceProbe)
        every { fragment.requireContext() } returns mockk(relaxed = true)
        every { XrDeviceProbe.isXrDevice(any()) } returns false

        helper = GeneralSettingsLauncherHelper(
            binding = binding,
            fragment = fragment,
            launcherModeContract = launcherModeContract,
            launcherRoleManager = launcherRoleManager,
            launcherStartWindowManager = launcherStartWindowManager,
            launcherRoleLauncher = launcherRoleLauncher,
            scopeProvider = { CoroutineScope(dispatcherRule.testDispatcher) },
            ioDispatcher = dispatcherRule.testDispatcher,
        )
    }

    private fun setBindingField(fieldName: String, value: Any) {
        var clazz: Class<*>? = binding.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(binding, value)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
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
    fun `setup keeps the start window row but hides system launcher controls on an XR device`() = runTest(
        dispatcherRule.testDispatcher
    ) {
        every { XrDeviceProbe.isXrDevice(any()) } returns true
        every { launcherRoleManager.readState() } returns LauncherRoleManager.LauncherModeState(false, false, false)

        helper.setup()
        advanceUntilIdle()

        verify { rowLauncherStartWindow.setCheckedSilently(true) }
        verify { rowLauncherModeEnabled.visibility = View.GONE }
        verify { rowLauncherSettings.visibility = View.GONE }
    }

    @Test
    fun `refreshState does not alter toggle when role request is pending`() = runTest(dispatcherRule.testDispatcher) {
        every { launcherRoleManager.readState() } returns LauncherRoleManager.LauncherModeState(true, false, false)

        helper.refreshState()
        advanceUntilIdle()

        verify(exactly = 0) { rowLauncherModeEnabled.setCheckedSilently(any()) }
    }

    @Test
    fun `refreshState sets toggle enabled when role is held`() = runTest(dispatcherRule.testDispatcher) {
        every { launcherRoleManager.readState() } returns LauncherRoleManager.LauncherModeState(false, true, true)

        helper.refreshState()
        advanceUntilIdle()

        verify { rowLauncherModeEnabled.setCheckedSilently(true) }
        verify { rowLauncherSettings.isEnabled = true }
        verify(exactly = 0) { launcherRoleManager.disableModeForBackgroundRefresh() }
    }

    @Test
    fun `refreshState disables mode and turns toggle off when role is not held and mode was enabled`() = runTest(
        dispatcherRule.testDispatcher
    ) {
        every { launcherRoleManager.readState() } returns LauncherRoleManager.LauncherModeState(false, false, true)

        helper.refreshState()
        advanceUntilIdle()

        verify(exactly = 1) { launcherRoleManager.disableModeForBackgroundRefresh() }
        verify { rowLauncherModeEnabled.setCheckedSilently(false) }
        verify { rowLauncherSettings.isEnabled = false }
    }

    @Test
    fun `refreshState sets toggle off when role is not held and mode was disabled`() = runTest(
        dispatcherRule.testDispatcher
    ) {
        every { launcherRoleManager.readState() } returns LauncherRoleManager.LauncherModeState(false, false, false)

        helper.refreshState()
        advanceUntilIdle()

        verify(exactly = 0) { launcherRoleManager.disableModeForBackgroundRefresh() }
        verify { rowLauncherModeEnabled.setCheckedSilently(false) }
        verify { rowLauncherSettings.isEnabled = false }
    }
}
