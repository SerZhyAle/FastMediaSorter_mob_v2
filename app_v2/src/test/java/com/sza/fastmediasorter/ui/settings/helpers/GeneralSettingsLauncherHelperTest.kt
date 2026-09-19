package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Intent
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.core.launcher.LauncherPrimaryWindowManager
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.core.util.XrDeviceProbe
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.launcher.LauncherPrimaryWindow
import com.sza.fastmediasorter.testing.MainDispatcherRule
import com.sza.fastmediasorter.ui.common.widget.SettingsDropdownRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * S2381 / S3024: tests for [GeneralSettingsLauncherHelper] primary window selection and role state synchronization.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeneralSettingsLauncherHelperTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val binding = mockk<FragmentSettingsGeneralBinding>(relaxed = true)
    private val rowLauncherPrimaryWindow = mockk<SettingsDropdownRow>(relaxed = true)
    private val rowLauncherSettings = mockk<MaterialButton>(relaxed = true)
    private val fragment = mockk<Fragment>(relaxed = true)
    private val activity = mockk<FragmentActivity>(relaxed = true)
    private val launcherModeContract = mockk<LauncherModeContract>()
    private val launcherRoleManager = mockk<LauncherRoleManager>(relaxed = true)
    private val launcherPrimaryWindowManager = mockk<LauncherPrimaryWindowManager>(relaxed = true)
    private val launcherRoleLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)

    private lateinit var helper: GeneralSettingsLauncherHelper

    @Before
    fun setUp() {
        setBindingField("rowLauncherPrimaryWindow", rowLauncherPrimaryWindow)
        setBindingField("rowLauncherSettings", rowLauncherSettings)
        every { launcherModeContract.isAvailableInBuild } returns true
        every { launcherPrimaryWindowManager.getCurrentChoice() } returns LauncherPrimaryWindow.DESKTOP
        mockkObject(XrDeviceProbe)
        every { fragment.requireContext() } returns mockk(relaxed = true)
        every { fragment.activity } returns activity
        every { XrDeviceProbe.isXrDevice(any()) } returns false

        helper = GeneralSettingsLauncherHelper(
            binding = binding,
            fragment = fragment,
            launcherModeContract = launcherModeContract,
            launcherRoleManager = launcherRoleManager,
            launcherPrimaryWindowManager = launcherPrimaryWindowManager,
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

        verify(exactly = 0) { launcherRoleManager.readState() }
        verify(exactly = 0) { rowLauncherPrimaryWindow.setSelection(any()) }
    }

    @Test
    fun `setup hides controls on an XR device`() = runTest(dispatcherRule.testDispatcher) {
        every { XrDeviceProbe.isXrDevice(any()) } returns true
        every { launcherRoleManager.readState() } returns LauncherRoleManager.LauncherModeState(false, false, false)

        helper.setup()
        advanceUntilIdle()

        verify { rowLauncherPrimaryWindow.visibility = View.GONE }
        verify { rowLauncherSettings.visibility = View.GONE }
    }

    @Test
    fun `selection of Home screen triggers launcher primary window manager and opens role request`() = runTest(
        dispatcherRule.testDispatcher
    ) {
        val listenerSlot = slot<(Int) -> Unit>()
        every { rowLauncherPrimaryWindow.setOnItemSelectedListener(capture(listenerSlot)) } returns Unit
        val roleIntent = mockk<Intent>()
        every { launcherRoleManager.enableModeForRequest() } returns roleIntent

        helper.setup()
        advanceUntilIdle()

        listenerSlot.captured.invoke(LauncherPrimaryWindow.HOME_SCREEN.ordinal)
        advanceUntilIdle()

        verify { launcherPrimaryWindowManager.applyChoice(LauncherPrimaryWindow.HOME_SCREEN) }
        verify { launcherRoleManager.enableModeForRequest() }
        verify { launcherRoleLauncher.launch(roleIntent) }
        verify { rowLauncherSettings.isEnabled = true }
    }

    @Test
    fun `selection of Desktop as primary window applies choice and disables role mode`() = runTest(
        dispatcherRule.testDispatcher
    ) {
        val listenerSlot = slot<(Int) -> Unit>()
        every { rowLauncherPrimaryWindow.setOnItemSelectedListener(capture(listenerSlot)) } returns Unit

        helper.setup()
        advanceUntilIdle()

        listenerSlot.captured.invoke(LauncherPrimaryWindow.DESKTOP.ordinal)
        advanceUntilIdle()

        verify { launcherPrimaryWindowManager.applyChoice(LauncherPrimaryWindow.DESKTOP) }
        verify(exactly = 0) { launcherRoleManager.enableModeForRequest() }
        verify { rowLauncherSettings.isEnabled = true }
    }

    @Test
    fun `selection of Resource Manager applies choice, disables role mode and disables launcher settings`() = runTest(
        dispatcherRule.testDispatcher
    ) {
        val listenerSlot = slot<(Int) -> Unit>()
        every { rowLauncherPrimaryWindow.setOnItemSelectedListener(capture(listenerSlot)) } returns Unit

        helper.setup()
        advanceUntilIdle()

        listenerSlot.captured.invoke(LauncherPrimaryWindow.RESOURCE_MANAGER.ordinal)
        advanceUntilIdle()

        verify { launcherPrimaryWindowManager.applyChoice(LauncherPrimaryWindow.RESOURCE_MANAGER) }
        verify(exactly = 0) { launcherRoleManager.enableModeForRequest() }
        verify { rowLauncherSettings.isEnabled = false }
    }

    @Test
    fun `refreshState selects Home screen when role is held`() = runTest(dispatcherRule.testDispatcher) {
        every { launcherRoleManager.readState() } returns LauncherRoleManager.LauncherModeState(false, true, true)
        every { launcherPrimaryWindowManager.getCurrentChoice() } returns LauncherPrimaryWindow.HOME_SCREEN

        helper.refreshState()
        advanceUntilIdle()

        verify { rowLauncherPrimaryWindow.setSelection(LauncherPrimaryWindow.HOME_SCREEN.ordinal) }
        verify { rowLauncherSettings.isEnabled = true }
        verify(exactly = 0) { launcherRoleManager.disableModeForBackgroundRefresh() }
    }

    @Test
    fun `refreshState disables mode and updates selection when role is not held and mode was enabled`() = runTest(
        dispatcherRule.testDispatcher
    ) {
        every { launcherRoleManager.readState() } returns LauncherRoleManager.LauncherModeState(false, false, true)
        every { launcherPrimaryWindowManager.getCurrentChoice() } returns LauncherPrimaryWindow.RESOURCE_MANAGER

        helper.refreshState()
        advanceUntilIdle()

        verify(exactly = 1) { launcherRoleManager.disableModeForBackgroundRefresh() }
        verify { rowLauncherPrimaryWindow.setSelection(LauncherPrimaryWindow.RESOURCE_MANAGER.ordinal) }
        verify { rowLauncherSettings.isEnabled = false }
    }

    @Test
    fun `refreshState selects Resource Manager when role is not held and mode was disabled`() = runTest(
        dispatcherRule.testDispatcher
    ) {
        every { launcherRoleManager.readState() } returns LauncherRoleManager.LauncherModeState(false, false, false)
        every { launcherPrimaryWindowManager.getCurrentChoice() } returns LauncherPrimaryWindow.RESOURCE_MANAGER

        helper.refreshState()
        advanceUntilIdle()

        verify(exactly = 0) { launcherRoleManager.disableModeForBackgroundRefresh() }
        verify { rowLauncherPrimaryWindow.setSelection(LauncherPrimaryWindow.RESOURCE_MANAGER.ordinal) }
        verify { rowLauncherSettings.isEnabled = false }
    }
}
