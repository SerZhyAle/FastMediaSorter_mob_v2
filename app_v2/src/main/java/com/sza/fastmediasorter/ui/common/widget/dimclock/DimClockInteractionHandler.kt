package com.sza.fastmediasorter.ui.common.widget.dimclock

/**
 * S3366: the dim clock block's gesture semantics, owned by the flavor that owns the clock's state -
 * the launcher flavor mirrors its desktop widget here, every other flavor declines.
 *
 * [onClockTap] and [onClockLongPress] receive the dim-dismiss prelude instead of running bare: an
 * action that starts an activity must dismiss the dim overlay first (ADR-2), and only the handler
 * knows whether its target exists at all - so it calls [dismissDim] itself, exactly between
 * resolving the target and starting it. A declined gesture returns false and the touch falls
 * through to the dim host's own handling.
 */
interface DimClockInteractionHandler {
    fun onFling(
        distanceX: Float,
        distanceY: Float,
        velocityX: Float,
        velocityY: Float,
        touchSlop: Float,
        minimumFlingVelocity: Float,
    ): Boolean

    fun onClockTap(dismissDim: () -> Unit): Boolean

    fun onClockLongPress(dismissDim: () -> Unit): Boolean
}
