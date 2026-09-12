package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.sza.fastmediasorter.core.launcher.LauncherStartWindowManager
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import timber.log.Timber

/**
 * S2811: the app's single answer to "which window does a cold start open".
 *
 * Only a plain launcher-icon start is eligible. A deep link, a share, the return to settings, the return
 * after a locale change and an audio resume each carry a destination of their own, so handing any of them
 * to the desktop would lose what the user actually asked for.
 *
 * S2858: when the app already holds the HOME role the redirect is suppressed. The desktop is then the Home
 * button destination, so the app icon should open the main file list instead of bouncing the user back to
 * the desktop they can already reach.
 */
class MainStartWindowRedirectManager(
    private val contract: LauncherModeContract,
    private val startWindowManager: LauncherStartWindowManager,
    private val isResumingAudio: () -> Boolean,
    private val isHomeRoleHeld: () -> Boolean,
) {

    /**
     * Starts the desktop and finishes [activity] when this launch is a plain cold start and the setting
     * is on. Returns false and touches nothing otherwise, so the caller continues its normal startup.
     */
    fun redirectIfRequested(
        activity: Activity,
        intent: Intent?,
        savedInstanceState: Bundle?,
        returnToSettingsRequested: Boolean,
    ): Boolean {
        val eligible = !returnToSettingsRequested &&
            isPlainColdLaunch(intent, savedInstanceState) &&
            !isResumingAudio() &&
            contract.isAvailableInBuild &&
            startWindowManager.isEnabled() &&
            !isHomeRoleHeld()
        Timber.d("S2811: redirect eligible=$eligible")
        Timber.d("S2858: homeRoleHeld=${isHomeRoleHeld()}")
        val startWindow = if (eligible) contract.startWindowIntent(activity) else null
        startWindow?.let {
            activity.startActivity(it)
            activity.finish()
        }
        return startWindow != null
    }

    // Data or extras mean the launch carries a destination - a share, a deep link, or the
    // return-to-settings request the settings screen sets on a restart.
    private fun isPlainColdLaunch(intent: Intent?, savedInstanceState: Bundle?): Boolean =
        savedInstanceState == null &&
            intent != null &&
            intent.action == Intent.ACTION_MAIN &&
            intent.data == null &&
            intent.extras == null
}
