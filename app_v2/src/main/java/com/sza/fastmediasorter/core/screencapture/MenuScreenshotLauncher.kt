package com.sza.fastmediasorter.core.screencapture

import android.app.Activity
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

/**
 * Store-safe, user-initiated confirmable screenshot trigger (S0559). Implementations start the
 * platform MediaProjection consent flow. Bound as an empty multibinding by default, so flavors
 * without the capture engine resolve an empty set and the menu action stays hidden.
 */
interface MenuScreenshotLauncher {
    fun launch(activity: Activity, action: ScreenshotGestureAction = ScreenshotGestureAction.SILENT_SCREENSHOT)
}
