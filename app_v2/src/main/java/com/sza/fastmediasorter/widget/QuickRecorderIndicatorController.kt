package com.sza.fastmediasorter.widget

import android.content.Context

/**
 * S0930: optional floating indicator shown over the current foreground app while
 * [QuickAudioRecorderService] is actively recording. Empty on flavors without the draw-over-apps
 * permission - mirrors the empty-set degradation pattern of
 * [com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController] - so the service
 * degrades to its existing notification Stop action and the S0796 repeat-gesture toggle when no
 * controller is bound or [isAvailable] is false.
 */
interface QuickRecorderIndicatorController {

    /** True when the OS permission backing the overlay is granted - checked before every [show]. */
    fun isAvailable(context: Context): Boolean

    /** Shows the floating indicator; [onStop] is invoked when the user taps its Stop control. */
    fun show(context: Context, onStop: () -> Unit)

    /** Updates the elapsed-time text already shown by [show]. No-op if not currently shown. */
    fun updateElapsed(text: String)

    /** Hides the indicator if shown. Safe to call when not shown. */
    fun hide()
}
