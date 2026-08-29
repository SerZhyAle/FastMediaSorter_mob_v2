package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Context
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherAllAppsGestureManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `upward swipe dispatches at the bottom boundary`() {
        val harness = GestureHarness(canScrollDown = false)

        harness.swipe(fromX = 100f, fromY = 300f, toX = 100f, toY = 100f)

        assertEquals(listOf(LauncherAllAppsGestureManager.DesktopSwipeDirection.UP), harness.swipes)
    }

    @Test
    fun `upward swipe does not dispatch before the bottom boundary`() {
        val harness = GestureHarness(canScrollDown = true)

        harness.swipe(fromX = 100f, fromY = 300f, toX = 100f, toY = 100f)

        assertTrue(harness.swipes.isEmpty())
    }

    @Test
    fun `upward swipe dispatches on an unscrollable desktop`() {
        val harness = GestureHarness(canScrollDown = false, canScrollUp = false)

        harness.swipe(fromX = 100f, fromY = 300f, toX = 100f, toY = 100f)

        assertEquals(listOf(LauncherAllAppsGestureManager.DesktopSwipeDirection.UP), harness.swipes)
    }

    @Test
    fun `swipe does not dispatch in edit mode`() {
        val harness = GestureHarness(isEnabled = false)

        harness.swipe(fromX = 100f, fromY = 300f, toX = 100f, toY = 100f)

        assertTrue(harness.swipes.isEmpty())
    }

    @Test
    fun `equal diagonal swipe is rejected`() {
        val harness = GestureHarness()

        harness.swipe(fromX = 100f, fromY = 300f, toX = 300f, toY = 100f)

        assertTrue(harness.swipes.isEmpty())
    }

    @Test
    fun `horizontal swipe dispatches without vertical boundary checks`() {
        val harness = GestureHarness(canScrollDown = true, canScrollUp = true)

        harness.swipe(fromX = 100f, fromY = 100f, toX = 300f, toY = 100f)

        assertEquals(listOf(LauncherAllAppsGestureManager.DesktopSwipeDirection.RIGHT), harness.swipes)
    }

    private inner class GestureHarness(
        canScrollDown: Boolean = false,
        canScrollUp: Boolean = false,
        isEnabled: Boolean = true,
    ) {
        val container = View(context)
        val swipes = mutableListOf<LauncherAllAppsGestureManager.DesktopSwipeDirection>()

        private val viewport = object : View(context) {
            override fun canScrollVertically(direction: Int): Boolean = when (direction) {
                1 -> canScrollDown
                -1 -> canScrollUp
                else -> false
            }
        }

        init {
            LauncherAllAppsGestureManager(
                container = container,
                viewport = viewport,
                isEnabled = { isEnabled },
                onSwipe = swipes::add,
            ).attach()
        }

        fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float) {
            container.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 0L, fromX, fromY))
            container.dispatchTouchEvent(
                event(
                    MotionEvent.ACTION_MOVE,
                    FLING_DURATION_MS / 2,
                    (fromX + toX) / 2,
                    (fromY + toY) / 2,
                )
            )
            container.dispatchTouchEvent(event(MotionEvent.ACTION_UP, FLING_DURATION_MS, toX, toY))
        }

        private fun event(action: Int, eventTime: Long, x: Float, y: Float): MotionEvent =
            MotionEvent.obtain(0L, eventTime, action, x, y, 0)
    }

    private companion object {
        const val FLING_DURATION_MS = 100L
    }
}
