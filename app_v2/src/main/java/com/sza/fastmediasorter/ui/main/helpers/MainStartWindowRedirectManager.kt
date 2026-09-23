package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.sza.fastmediasorter.core.launcher.LauncherStartWindowManager
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract

/** Routes only an ordinary cold app-icon launch to the optional desktop start window. */
class MainStartWindowRedirectManager(
    private val contract: LauncherModeContract,
    private val startWindowManager: LauncherStartWindowManager,
    private val isResumingAudio: () -> Boolean,
) {
    fun redirectIfRequested(
        activity: Activity,
        intent: Intent?,
        savedInstanceState: Bundle?,
        returnToSettingsRequested: Boolean,
    ): Boolean {
        val eligible = !returnToSettingsRequested &&
            isPlainColdLaunch(intent, savedInstanceState) &&
            !isResumingAudio() &&
            startWindowManager.isEnabled()
        val startWindow = if (eligible) contract.startWindowIntent(activity) else null
        startWindow?.let {
            activity.startActivity(it)
            activity.finish()
        }
        return startWindow != null
    }

    private fun isPlainColdLaunch(intent: Intent?, savedInstanceState: Bundle?): Boolean =
        savedInstanceState == null &&
            intent?.action == Intent.ACTION_MAIN &&
            intent.data == null &&
            intent.extras == null
}
