package com.sza.fastmediasorter.ui.launcher.helpers

import android.app.Activity
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.core.screencapture.ScreenshotGestureActionDispatcher
import com.sza.fastmediasorter.domain.model.LauncherAllAppsSwipeAction
import timber.log.Timber

/**
 * S2304: routes actions configured for directional swipes on the launcher All apps panel.
 *
 * The two panel-local routes arrive as callbacks rather than as work done here - each acts on the panel
 * that is already on screen, which this handler does not own.
 */
class LauncherAllAppsSwipeActionHandler(
    private val activity: Activity,
    private val actionDispatcher: ScreenshotGestureActionDispatcher,
    private val screenshotLaunchers: Set<@JvmSuppressWildcards MenuScreenshotLauncher>,
    private val onBackToDesktop: () -> Unit,
    private val onExpandAllApps: () -> Unit,
) {

    suspend fun handle(action: LauncherAllAppsSwipeAction, payload: String) {
        Timber.d("S2304: execute all apps swipe action=%s", action.persistedName)
        when (action) {
            LauncherAllAppsSwipeAction.BackToDesktop -> onBackToDesktop()
            LauncherAllAppsSwipeAction.ExpandAllApps -> onExpandAllApps()
            is LauncherAllAppsSwipeAction.EdgeGestureAction -> {
                val handled = actionDispatcher.handlePreCaptureAction(activity, action.action, payload)
                if (!handled) screenshotLaunchers.firstOrNull()?.launch(activity, action.action)
            }
        }
    }
}
