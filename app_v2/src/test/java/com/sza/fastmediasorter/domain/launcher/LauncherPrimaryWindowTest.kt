package com.sza.fastmediasorter.domain.launcher

import com.sza.fastmediasorter.data.model.DeviceProfileType
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("FunctionNaming") // backtick test names, project convention
class LauncherPrimaryWindowTest {

    @Test
    fun `choicesFor offerable keeps all three in enum order`() {
        assertEquals(
            listOf(
                LauncherPrimaryWindow.HOME_SCREEN,
                LauncherPrimaryWindow.DESKTOP,
                LauncherPrimaryWindow.RESOURCE_MANAGER,
            ),
            LauncherPrimaryWindow.choicesFor(true),
        )
    }

    @Test
    fun `choicesFor not offerable drops only HOME_SCREEN`() {
        assertEquals(
            listOf(LauncherPrimaryWindow.DESKTOP, LauncherPrimaryWindow.RESOURCE_MANAGER),
            LauncherPrimaryWindow.choicesFor(false),
        )
    }

    @Test
    fun `smartphone degrades to DESKTOP where the role is not offerable`() {
        assertEquals(
            LauncherPrimaryWindow.DESKTOP,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.PERSONAL_SMARTPHONE, homeRoleOfferable = false),
        )
    }

    @Test
    fun `vr headset keeps RESOURCE_MANAGER where the role is not offerable`() {
        assertEquals(
            LauncherPrimaryWindow.RESOURCE_MANAGER,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.VR_HEADSET, homeRoleOfferable = false),
        )
    }

    @Test
    fun `desktop platform recommends DESKTOP for every profile`() {
        assertEquals(
            LauncherPrimaryWindow.DESKTOP,
            LauncherPrimaryWindow.recommendedFor(null, homeRoleOfferable = false, desktopPlatform = true),
        )
        assertEquals(
            LauncherPrimaryWindow.DESKTOP,
            LauncherPrimaryWindow.recommendedFor(
                DeviceProfileType.OTHER,
                homeRoleOfferable = false,
                desktopPlatform = true,
            ),
        )
    }

    @Test
    fun `default arguments keep the smartphone on HOME_SCREEN`() {
        assertEquals(
            LauncherPrimaryWindow.HOME_SCREEN,
            LauncherPrimaryWindow.recommendedFor(DeviceProfileType.PERSONAL_SMARTPHONE),
        )
    }
}
