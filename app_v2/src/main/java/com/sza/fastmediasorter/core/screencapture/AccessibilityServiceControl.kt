package com.sza.fastmediasorter.core.screencapture

/**
 * Cross-flavor contract for controlling theAccessibilityService liveness (disableSelf).
 *
 * Implemented concretely on noLegal (which hosts ScreenshotAccessibilityService).
 * On all other flavors, the default [NoOp] implementation is bound, returning false.
 */
interface AccessibilityServiceControl {

    fun isServiceActive(): Boolean

    fun openPowerDialog(): Boolean

    fun disableSelf(): Boolean

    object NoOp : AccessibilityServiceControl {
        override fun isServiceActive(): Boolean = false

        override fun openPowerDialog(): Boolean = false

        override fun disableSelf(): Boolean = false
    }
}
