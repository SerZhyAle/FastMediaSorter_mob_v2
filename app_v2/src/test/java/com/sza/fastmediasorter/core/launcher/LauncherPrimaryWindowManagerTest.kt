package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.launcher.LauncherPrimaryWindow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LauncherPrimaryWindowManagerTest {

    private val contract = mockk<LauncherModeContract>()
    private val roleManager = mockk<LauncherRoleManager>(relaxed = true)
    private val startWindowManager = mockk<LauncherStartWindowManager>(relaxed = true)

    private lateinit var manager: LauncherPrimaryWindowManager

    @Before
    fun setUp() {
        every { contract.isAvailableInBuild } returns true
        manager = LauncherPrimaryWindowManager(contract, roleManager, startWindowManager)
    }

    @Test
    fun `isAvailable delegates to launcher mode contract`() {
        assertTrue(manager.isAvailable)

        every { contract.isAvailableInBuild } returns false
        assertFalse(manager.isAvailable)
    }

    @Test
    fun `applyChoice with HOME_SCREEN enables start window and marks home candidate with pending request`() {
        manager.applyChoice(LauncherPrimaryWindow.HOME_SCREEN)

        verify { startWindowManager.setEnabled(true) }
        verify { roleManager.markAsHomeCandidate() }
        verify { roleManager.markRoleRequestPending() }
    }

    @Test
    fun `applyChoice with DESKTOP enables start window and disables home mode`() {
        manager.applyChoice(LauncherPrimaryWindow.DESKTOP)

        verify { startWindowManager.setEnabled(true) }
        verify { roleManager.disableMode() }
        verify { roleManager.clearRoleRequestPending() }
    }

    @Test
    fun `applyChoice with RESOURCE_MANAGER disables start window and disables home mode`() {
        manager.applyChoice(LauncherPrimaryWindow.RESOURCE_MANAGER)

        verify { startWindowManager.setEnabled(false) }
        verify { roleManager.disableMode() }
        verify { roleManager.clearRoleRequestPending() }
    }

    @Test
    fun `applyChoice does nothing when not available in build`() {
        every { contract.isAvailableInBuild } returns false

        manager.applyChoice(LauncherPrimaryWindow.HOME_SCREEN)

        verify(exactly = 0) { startWindowManager.setEnabled(any()) }
        verify(exactly = 0) { roleManager.markAsHomeCandidate() }
        verify(exactly = 0) { roleManager.markRoleRequestPending() }
    }

    @Test
    fun `getCurrentChoice returns HOME_SCREEN when home role is held or pending`() {
        every { roleManager.readState() } returns LauncherRoleManager.LauncherModeState(
            roleRequestPending = false,
            homeRoleHeld = true,
            modeEnabled = true,
        )

        assertEquals(LauncherPrimaryWindow.HOME_SCREEN, manager.getCurrentChoice())
    }

    @Test
    fun `getCurrentChoice returns DESKTOP when start window is enabled and not home`() {
        every { roleManager.readState() } returns LauncherRoleManager.LauncherModeState(
            roleRequestPending = false,
            homeRoleHeld = false,
            modeEnabled = false,
        )
        every { startWindowManager.isEnabled() } returns true

        assertEquals(LauncherPrimaryWindow.DESKTOP, manager.getCurrentChoice())
    }

    @Test
    fun `getCurrentChoice returns RESOURCE_MANAGER when start window is disabled and not home`() {
        every { roleManager.readState() } returns LauncherRoleManager.LauncherModeState(
            roleRequestPending = false,
            homeRoleHeld = false,
            modeEnabled = false,
        )
        every { startWindowManager.isEnabled() } returns false

        assertEquals(LauncherPrimaryWindow.RESOURCE_MANAGER, manager.getCurrentChoice())
    }

    @Test
    fun `recommendedFor maps smartphone tablet tv and car to HOME_SCREEN`() {
        assertEquals(
            LauncherPrimaryWindow.HOME_SCREEN,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.PERSONAL_SMARTPHONE)
        )
        assertEquals(
            LauncherPrimaryWindow.HOME_SCREEN,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.HOME_TABLET)
        )
        assertEquals(
            LauncherPrimaryWindow.HOME_SCREEN,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.TV_MEDIA_BOX)
        )
        assertEquals(
            LauncherPrimaryWindow.HOME_SCREEN,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.CAR_HEAD_UNIT)
        )
    }

    @Test
    fun `recommendedFor maps players frame reader vr other and null to RESOURCE_MANAGER`() {
        assertEquals(
            LauncherPrimaryWindow.RESOURCE_MANAGER,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.MEDIA_PLAYER)
        )
        assertEquals(
            LauncherPrimaryWindow.RESOURCE_MANAGER,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.PHOTO_FRAME)
        )
        assertEquals(
            LauncherPrimaryWindow.RESOURCE_MANAGER,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.VIDEO_PLAYER)
        )
        assertEquals(
            LauncherPrimaryWindow.RESOURCE_MANAGER,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.AUDIO_PLAYER)
        )
        assertEquals(
            LauncherPrimaryWindow.RESOURCE_MANAGER,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.EBOOK_READER)
        )
        assertEquals(
            LauncherPrimaryWindow.RESOURCE_MANAGER,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.VR_HEADSET)
        )
        assertEquals(
            LauncherPrimaryWindow.RESOURCE_MANAGER,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.OTHER)
        )
        assertEquals(LauncherPrimaryWindow.RESOURCE_MANAGER, LauncherPrimaryWindow.recommendedFor(null))
    }
}
