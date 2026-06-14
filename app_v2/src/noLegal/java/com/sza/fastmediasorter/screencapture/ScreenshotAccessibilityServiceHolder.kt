package com.sza.fastmediasorter.screencapture

/**
 * Process-wide handle to the running [ScreenshotAccessibilityService]. The settings controller
 * cannot enable/disable an accessibility service programmatically, so it pushes overlay-visibility
 * changes to the live instance through this holder. Null when the service is not connected.
 */
internal object ScreenshotAccessibilityServiceHolder {
    @Volatile
    var instance: ScreenshotAccessibilityService? = null
}
