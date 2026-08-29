package com.sza.fastmediasorter.ui.launcher.helpers

import android.app.Activity
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.core.screencapture.ScreenshotGestureActionDispatcher
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import timber.log.Timber

/** Routes actions configured for directional swipes on the launcher desktop. */
class LauncherDesktopSwipeActionHandler(
    private val activity: Activity,
    private val actionDispatcher: ScreenshotGestureActionDispatcher,
    private val screenshotLaunchers: Set<@JvmSuppressWildcards MenuScreenshotLauncher>,
    private val onOpenAllApps: () -> Unit,
) {

    suspend fun handle(action: LauncherDesktopSwipeAction, payload: String) {
        Timber.d("S2221: execute desktop swipe action=%s", action)
        when (action) {
            LauncherDesktopSwipeAction.OpenAllApps -> onOpenAllApps()
            is LauncherDesktopSwipeAction.EdgeGestureAction -> {
                val handled = actionDispatcher.handlePreCaptureAction(activity, action.action, payload)
                if (!handled) screenshotLaunchers.firstOrNull()?.launch(activity, action.action)
            }
        }
    }
}
