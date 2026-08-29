package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.core.screencapture.gesture.GestureAccessibilityActions
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import timber.log.Timber

/** Routes actions configured for directional swipes on the launcher desktop. */
class LauncherDesktopSwipeActionHandler(
    private val accessibilityActions: Set<@JvmSuppressWildcards GestureAccessibilityActions>,
    private val onOpenAllApps: () -> Unit,
) {

    fun handle(action: LauncherDesktopSwipeAction) {
        Timber.d("S2221: execute desktop swipe action=%s", action)
        when (action) {
            LauncherDesktopSwipeAction.OPEN_ALL_APPS -> onOpenAllApps()
            LauncherDesktopSwipeAction.OPEN_NOTIFICATION_SHADE ->
                accessibilityActions.firstOrNull()?.perform(ScreenshotGestureAction.OPEN_NOTIFICATION_SHADE)
            LauncherDesktopSwipeAction.DO_NOT_USE -> Unit
        }
    }
}
