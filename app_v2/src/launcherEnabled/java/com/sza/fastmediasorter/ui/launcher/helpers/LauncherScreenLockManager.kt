package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.core.screencapture.gesture.GestureAccessibilityActions
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import timber.log.Timber

/**
 * S2268 / S2384: the launcher's single "turn the screen off" decision point.
 *
 * Both ways the desktop can go dark come through here - the double-tap gesture and the inactivity
 * countdown - so the two cannot drift into different behaviour again, which is the divergence S2384
 * was opened for. It is also the one place a future system-lock privilege for `standard` would be
 * added (S1902 owns that decision).
 *
 * The real device lock exists only behind the accessibility seam, which is bound on `noLegal` and only
 * while its service is enabled. Everywhere else the screen degrades to the desktop's own black screen -
 * the same overlay its "Black screen" cell action raises, which is the substitute the owner named
 * (ruling 2026-08-31). An app-private overlay, not a system lock: the backlight stays on and no system
 * AOD appears, which is why the caller is told which of the two happened.
 *
 * @param accessibilityActions the flavor seam; empty on every flavor but `noLegal`.
 * @param showBlackScreen raises the launcher's black-screen overlay.
 */
class LauncherScreenLockManager(
    private val accessibilityActions: Set<GestureAccessibilityActions>,
    private val showBlackScreen: () -> Unit,
) {

    /**
     * @param allowSystemLock false keeps the decision inside the app: the overlay is raised even where
     *   the real lock is reachable. S3285 - the idle countdown passes false while the global
     *   prevent-sleep hold stands, because handing the desktop to the system lock is exactly the
     *   system sleep that setting exists to forbid. An explicit gesture still passes true: asking for
     *   the lock is not being idle.
     * @return true when the device really locked, false when the app-private overlay was raised instead.
     */
    fun turnScreenOff(allowSystemLock: Boolean = true): Boolean {
        val locked = allowSystemLock &&
            accessibilityActions.any { it.perform(ScreenshotGestureAction.LOCK_SCREEN) }
        if (!locked) showBlackScreen()
        // The two outcomes look alike on a screenshot and different to the user, so which one ran is the
        // first thing any report about this feature needs; the seam count says why it degraded.
        Timber.d(
            "LauncherScreenLockManager: screen off requested, systemLock=%b, allowed=%b, seams=%d",
            locked,
            allowSystemLock,
            accessibilityActions.size,
        )
        return locked
    }
}
