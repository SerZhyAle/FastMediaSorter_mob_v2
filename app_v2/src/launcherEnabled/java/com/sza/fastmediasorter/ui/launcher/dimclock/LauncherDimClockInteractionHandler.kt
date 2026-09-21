package com.sza.fastmediasorter.ui.launcher.dimclock

import android.content.Context
import android.graphics.Color
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockInteractionHandler
import com.sza.fastmediasorter.ui.launcher.gadget.ClockDialRandomizer
import com.sza.fastmediasorter.ui.launcher.gadget.ClockGadgetStateStore
import com.sza.fastmediasorter.ui.launcher.gadget.ClockSwipeDirection
import com.sza.fastmediasorter.ui.launcher.gadget.ClockSwipeDirectionResolver
import com.sza.fastmediasorter.ui.launcher.gadget.openCalendarAtNow
import com.sza.fastmediasorter.ui.launcher.gadget.openSystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3366: the launcher's clock-widget gestures, replayed over the dim clock block on the same
 * [ClockGadgetStateStore] - so a swipe on the dimmed screen and a swipe on the desktop change the
 * same seconds flag, the same dial color and the same typeface, never two divergent copies.
 *
 * The open actions reuse the widget's own guarded helpers (S1906), passing the dim-dismiss prelude
 * in as the beforeStart hook: the overlay goes away only once the target is known to exist, and
 * always before the activity appears (ADR-2). The dial's random color is computed against black -
 * the dim screen's surface - while the widget computes against its own surface, which is what keeps
 * both readable on their respective backgrounds.
 */
@Singleton
class LauncherDimClockInteractionHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val stateStore: ClockGadgetStateStore,
) : DimClockInteractionHandler {

    override fun onFling(
        distanceX: Float,
        distanceY: Float,
        velocityX: Float,
        velocityY: Float,
        touchSlop: Float,
        minimumFlingVelocity: Float,
    ): Boolean {
        val direction = ClockSwipeDirectionResolver.resolve(
            distanceX = distanceX,
            distanceY = distanceY,
            velocityX = velocityX,
            velocityY = velocityY,
            touchSlop = touchSlop,
            minimumFlingVelocity = minimumFlingVelocity,
        ) ?: return false
        when (direction) {
            ClockSwipeDirection.RIGHT -> stateStore.setSecondsVisible(false)
            ClockSwipeDirection.LEFT -> stateStore.setSecondsVisible(true)
            ClockSwipeDirection.UP -> {
                stateStore.setDialColor(ClockDialRandomizer.nextDialColor(DIM_SURFACE_COLOR))
                stateStore.setDialTypefaceName(
                    ClockDialRandomizer.nextTypefaceName(stateStore.read().dialTypefaceName)
                )
            }
            ClockSwipeDirection.DOWN -> stateStore.setDialColor(null)
        }
        return true
    }

    override fun onClockTap(dismissDim: () -> Unit): Boolean {
        Timber.d("S3366: clock block tap -> system clock app")
        openSystemClock(context, dismissDim)
        return true
    }

    override fun onClockLongPress(dismissDim: () -> Unit): Boolean {
        Timber.d("S3366: clock block long press -> calendar")
        openCalendarAtNow(context, dismissDim)
        return true
    }

    private companion object {
        /** The dim overlay paints its clock on pure black; that is the surface the dial must read on. */
        val DIM_SURFACE_COLOR: Int = Color.BLACK
    }
}
