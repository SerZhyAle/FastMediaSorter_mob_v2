package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.launcher.LauncherPrimaryWindow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps the high-level primary window choice to launcher role and start window preferences.
 */
@Singleton
class LauncherPrimaryWindowManager @Inject constructor(
    private val contract: LauncherModeContract,
    private val roleManager: LauncherRoleManager,
    private val startWindowManager: LauncherStartWindowManager,
) {
    val isAvailable: Boolean
        get() = contract.isAvailableInBuild

    fun getCurrentChoice(): LauncherPrimaryWindow {
        if (!contract.isAvailableInBuild) {
            return LauncherPrimaryWindow.RESOURCE_MANAGER
        }
        val roleState = roleManager.readState()
        val isHome = roleState.homeRoleHeld || roleState.roleRequestPending || roleState.modeEnabled
        return when {
            isHome -> LauncherPrimaryWindow.HOME_SCREEN
            startWindowManager.isEnabled() -> LauncherPrimaryWindow.DESKTOP
            else -> LauncherPrimaryWindow.RESOURCE_MANAGER
        }
    }

    fun applyChoice(choice: LauncherPrimaryWindow) {
        if (!contract.isAvailableInBuild) return
        when (choice) {
            LauncherPrimaryWindow.HOME_SCREEN -> {
                startWindowManager.setEnabled(true)
                roleManager.markAsHomeCandidate()
                roleManager.markRoleRequestPending()
            }
            LauncherPrimaryWindow.DESKTOP -> {
                startWindowManager.setEnabled(true)
                roleManager.disableMode()
                roleManager.clearRoleRequestPending()
            }
            LauncherPrimaryWindow.RESOURCE_MANAGER -> {
                startWindowManager.setEnabled(false)
                roleManager.disableMode()
                roleManager.clearRoleRequestPending()
            }
        }
    }
}
