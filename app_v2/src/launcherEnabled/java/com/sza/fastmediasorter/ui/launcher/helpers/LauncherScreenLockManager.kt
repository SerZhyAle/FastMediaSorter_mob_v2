package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.core.screencapture.gesture.GestureAccessibilityActions
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import timber.log.Timber

/**
 * S2268: turns the desktop's "lock" gesture into the best screen lock this build can actually reach.
 *
 * The real device lock exists only behind the accessibility seam, which is bound on `noLegal` and only
 * while its service is enabled. Everywhere else the gesture used to do nothing visible, so it degrades to
 * the desktop's own black screen - the same overlay its "Black screen" cell action raises, which is the
 * substitute the owner named (ruling 2026-08-31). An app-private overlay, not a system lock.
 *
 * @param accessibilityActions the flavor seam; empty on every flavor but `noLegal`.
 * @param showBlackScreen raises the launcher's black-screen overlay.
 */
class LauncherScreenLockManager(
    private val accessibilityActions: Set<GestureAccessibilityActions>,
    private val showBlackScreen: () -> Unit,
) {

    fun lockScreen() {
        val locked = accessibilityActions.any { it.perform(ScreenshotGestureAction.LOCK_SCREEN) }
        Timber.d("S2268: desktop double tap lock, system lock performed=%s", locked)
        if (!locked) showBlackScreen()
    }
}
