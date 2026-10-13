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

    fun availableChoices(): List<LauncherPrimaryWindow> =
        LauncherPrimaryWindow.choicesFor(roleManager.isHomeRoleOfferable())

    fun getCurrentChoice(): LauncherPrimaryWindow {
        if (!contract.isAvailableInBuild) {
            return LauncherPrimaryWindow.RESOURCE_MANAGER
        }
        val roleState = roleManager.readState()
        val storedHome = roleState.homeRoleHeld || roleState.roleRequestPending || roleState.modeEnabled
        return when {
            // A home flag stored where the role is unreachable (Googlebook) reads as the desktop; no migration.
            storedHome && !roleManager.isHomeRoleOfferable() -> LauncherPrimaryWindow.DESKTOP
            storedHome -> LauncherPrimaryWindow.HOME_SCREEN
            startWindowManager.isEnabled() -> LauncherPrimaryWindow.DESKTOP
            else -> LauncherPrimaryWindow.RESOURCE_MANAGER
        }
    }

    fun applyChoice(choice: LauncherPrimaryWindow) {
        if (!contract.isAvailableInBuild) return
        val effective = if (choice == LauncherPrimaryWindow.HOME_SCREEN && !roleManager.isHomeRoleOfferable()) {
            LauncherPrimaryWindow.DESKTOP
        } else {
            choice
        }
        when (effective) {
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
