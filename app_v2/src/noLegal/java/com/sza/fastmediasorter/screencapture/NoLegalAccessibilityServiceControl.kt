package com.sza.fastmediasorter.screencapture

import com.sza.fastmediasorter.core.screencapture.AccessibilityServiceControl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [AccessibilityServiceControl] for noLegal flavor.
 *
 * Checks [ScreenshotAccessibilityServiceHolder.instance] to report liveness and call [disableSelf].
 */
@Singleton
class NoLegalAccessibilityServiceControl @Inject constructor() : AccessibilityServiceControl {

    override fun isServiceActive(): Boolean {
        return ScreenshotAccessibilityServiceHolder.instance != null
    }

    override fun disableSelf(): Boolean {
        val service = ScreenshotAccessibilityServiceHolder.instance ?: return false
        timber.log.Timber.d("S1881: disableSelf called")
        service.disableSelf()
        return true
    }
}
