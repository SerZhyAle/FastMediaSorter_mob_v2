package com.sza.fastmediasorter.ui.xr.helpers

import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime

class HudHapticBridge(private val runtime: DiagnosticXrRuntime) {

    /** Vibrates a controller for general feedback. (0 = left, 1 = right) */
    fun triggerFeedback(hand: Int = 1, durationSeconds: Float = 0.08f, amplitude: Float = 0.5f) {
        runtime.applyHaptic(hand, durationSeconds, 150f, amplitude)
    }

    /** Triggered when the pointer ray enters or exits interactive boundary. */
    fun triggerHoverFeedback(hand: Int = 1) {
        runtime.applyHaptic(hand, 0.05f, 100f, 0.3f)
    }

    /** Triggered on button click or slider adjustment. */
    fun triggerClickFeedback(hand: Int = 1) {
        runtime.applyHaptic(hand, 0.12f, 200f, 0.7f)
    }
}
