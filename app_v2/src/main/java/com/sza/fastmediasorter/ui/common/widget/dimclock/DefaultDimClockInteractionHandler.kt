package com.sza.fastmediasorter.ui.common.widget.dimclock

import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3366: the non-launcher dim clock keeps no gestures - every touch over the clock block falls
 * through to the dim host's exit semantics, which is that flavor's frozen behavior.
 */
@Singleton
class DefaultDimClockInteractionHandler @Inject constructor() : DimClockInteractionHandler {
    override fun onFling(
        distanceX: Float,
        distanceY: Float,
        velocityX: Float,
        velocityY: Float,
        touchSlop: Float,
        minimumFlingVelocity: Float,
    ): Boolean = false

    override fun onClockTap(dismissDim: () -> Unit): Boolean = false

    override fun onClockLongPress(dismissDim: () -> Unit): Boolean = false
}
