package com.sza.fastmediasorter.ui.launcher.gadget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies the directions that the clock widget maps to its display changes. */
class ClockSwipeDirectionResolverTest {

    @Test
    fun `horizontal flings resolve left and right`() {
        assertEquals(
            ClockSwipeDirection.RIGHT,
            ClockSwipeDirectionResolver.resolve(
                distanceX = 200f,
                distanceY = 10f,
                velocityX = 300f,
                velocityY = 20f,
                touchSlop = 20f,
                minimumFlingVelocity = 100f,
            ),
        )
        assertEquals(
            ClockSwipeDirection.LEFT,
            ClockSwipeDirectionResolver.resolve(
                distanceX = -200f,
                distanceY = 10f,
                velocityX = -300f,
                velocityY = 20f,
                touchSlop = 20f,
                minimumFlingVelocity = 100f,
            ),
        )
    }

    @Test
    fun `vertical flings resolve up and down`() {
        assertEquals(
            ClockSwipeDirection.UP,
            ClockSwipeDirectionResolver.resolve(
                distanceX = 10f,
                distanceY = -200f,
                velocityX = 20f,
                velocityY = -300f,
                touchSlop = 20f,
                minimumFlingVelocity = 100f,
            ),
        )
        assertEquals(
            ClockSwipeDirection.DOWN,
            ClockSwipeDirectionResolver.resolve(
                distanceX = 10f,
                distanceY = 200f,
                velocityX = 20f,
                velocityY = 300f,
                touchSlop = 20f,
                minimumFlingVelocity = 100f,
            ),
        )
    }

    @Test
    fun `slow movement does not change the clock`() {
        assertNull(
            ClockSwipeDirectionResolver.resolve(
                distanceX = 200f,
                distanceY = 10f,
                velocityX = 20f,
                velocityY = 10f,
                touchSlop = 20f,
                minimumFlingVelocity = 100f,
            )
        )
    }
}
