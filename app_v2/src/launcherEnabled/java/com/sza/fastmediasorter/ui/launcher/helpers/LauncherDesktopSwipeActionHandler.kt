package com.sza.fastmediasorter.ui.launcher.helpers

import android.app.Activity
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.core.screencapture.ScreenshotGestureActionDispatcher
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

internal fun resolveDesktopSwipeAction(
    direction: LauncherAllAppsGestureManager.DesktopSwipeDirection,
    screenIndex: Int,
    screenCount: Int,
    configuredAction: LauncherDesktopSwipeAction,
): LauncherDesktopSwipeAction = when (direction) {
    LauncherAllAppsGestureManager.DesktopSwipeDirection.LEFT ->
        if (screenIndex < screenCount - 1) LauncherDesktopSwipeAction.NextScreen else configuredAction
    LauncherAllAppsGestureManager.DesktopSwipeDirection.RIGHT ->
        if (screenIndex > 0) LauncherDesktopSwipeAction.PreviousScreen else configuredAction
    else -> configuredAction
}

/**
 * Routes actions configured for directional swipes on the launcher desktop.
 *
 * S2301: the launcher-local routes arrive as callbacks rather than as work done here - each acts
 * on the desktop that is already on screen, which this handler does not own.
 */
class LauncherDesktopSwipeActionHandler(
    private val activity: Activity,
    private val actionDispatcher: ScreenshotGestureActionDispatcher,
    private val screenshotLaunchers: Set<@JvmSuppressWildcards MenuScreenshotLauncher>,
    private val onOpenAllApps: () -> Unit,
    private val onNextScreen: () -> Unit,
    private val onPreviousScreen: () -> Unit,
    private val onBlackScreen: () -> Unit,
    private val onLockScreen: () -> Unit,
) {

    suspend fun handle(action: LauncherDesktopSwipeAction, payload: String) {
        when (action) {
            LauncherDesktopSwipeAction.OpenAllApps -> onOpenAllApps()
            LauncherDesktopSwipeAction.NextScreen -> onNextScreen()
            LauncherDesktopSwipeAction.PreviousScreen -> onPreviousScreen()
            LauncherDesktopSwipeAction.BlackScreen -> {
                onBlackScreen()
            }
            is LauncherDesktopSwipeAction.EdgeGestureAction -> {
                if (action.action == ScreenshotGestureAction.LOCK_SCREEN) {
                    onLockScreen()
                } else {
                    val handled = actionDispatcher.handlePreCaptureAction(activity, action.action, payload)
                    if (!handled) screenshotLaunchers.firstOrNull()?.launch(activity, action.action)
                }
            }
        }
    }
}
