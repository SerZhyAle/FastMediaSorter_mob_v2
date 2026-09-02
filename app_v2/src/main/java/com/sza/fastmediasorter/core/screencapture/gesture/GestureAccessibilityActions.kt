package com.sza.fastmediasorter.core.screencapture.gesture

import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

/**
 * S1038: flavor seam for the SYSTEM-group edge-gesture actions (notification shade, quick settings,
 * lock, split-screen, recents) that can only run through an accessibility service's global actions.
 * Bound `@IntoSet` on `noLegal` (the only flavor with the accessibility service); the set is empty on
 * every other flavor, which is how the picker keeps the SYSTEM group hidden where it cannot work.
 */
interface GestureAccessibilityActions {

    /**
     * Performs the SYSTEM-group [action], degrading with a log (not a crash) when the accessibility
     * service is not currently running or the action is unavailable at this API level.
     *
     * S2268: returns whether the action actually ran, so a caller with a fallback (the launcher's own
     * black screen when the device lock is out of reach) can tell a degraded no-op from a real lock.
     */
    fun perform(action: ScreenshotGestureAction): Boolean
}
