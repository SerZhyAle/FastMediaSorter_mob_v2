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

    @Test
    fun `swipe starting outside the viewport does not dispatch`() {
        val harness = GestureHarness(canScrollDown = false)

        val outside = VIEWPORT_SIZE + 100f
        harness.swipe(fromX = outside, fromY = outside + 200f, toX = outside, toY = outside)

        assertTrue(harness.swipes.isEmpty())
    }

    @Test
    fun `swipe starting on a desktop cell does not dispatch`() {
        val harness = GestureHarness(startsOnInteractiveCell = true)

        harness.swipe(fromX = 100f, fromY = 300f, toX = 100f, toY = 100f)

        assertTrue(harness.swipes.isEmpty())
    }

    @Test
    fun `double tap on free desktop dispatches callback`() {
        val harness = GestureHarness()

        harness.doubleTap()

        assertEquals(1, harness.doubleTaps)
    }

    @Test
    fun `double tap on desktop cell does not dispatch callback`() {
        val harness = GestureHarness(startsOnInteractiveCell = true)

        harness.doubleTap()

        assertEquals(0, harness.doubleTaps)
    }

    private inner class GestureHarness(
        canScrollDown: Boolean = false,
        canScrollUp: Boolean = false,
        isEnabled: Boolean = true,
        startsOnInteractiveCell: Boolean = false,
    ) {
        val container = View(context)
        val swipes = mutableListOf<LauncherAllAppsGestureManager.DesktopSwipeDirection>()
        var doubleTaps = 0

        private val viewport = object : View(context) {
            override fun canScrollVertically(direction: Int): Boolean = when (direction) {
                1 -> canScrollDown
                -1 -> canScrollUp
                else -> false
            }
        }

        init {
            // getGlobalVisibleRect() is false on a zero-sized view, which would close the
            // manager's viewport gate for every event and make each assertion below vacuous.
            viewport.layout(0, 0, VIEWPORT_SIZE, VIEWPORT_SIZE)
        }

        private val manager = LauncherAllAppsGestureManager(
            container = container,
            viewport = viewport,
            isEnabled = { isEnabled },
            isTouchOnInteractiveCell = { startsOnInteractiveCell },
            onSwipe = swipes::add,
            onDoubleTap = { doubleTaps++ },
        )

        fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float) {
            manager.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0L, fromX, fromY))
            manager.onTouchEvent(
                event(
                    MotionEvent.ACTION_MOVE,
                    FLING_DURATION_MS / 2,
                    (fromX + toX) / 2,
                    (fromY + toY) / 2,
                )
            )
            manager.onTouchEvent(event(MotionEvent.ACTION_UP, FLING_DURATION_MS, toX, toY))
        }

        fun doubleTap() {
            manager.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0L, 100f, 100f))
            manager.onTouchEvent(event(MotionEvent.ACTION_UP, 50L, 100f, 100f))
            manager.onTouchEvent(event(MotionEvent.ACTION_DOWN, 100L, 100f, 100f))
            manager.onTouchEvent(event(MotionEvent.ACTION_UP, 150L, 100f, 100f))
        }

        private fun event(action: Int, eventTime: Long, x: Float, y: Float): MotionEvent =
            MotionEvent.obtain(0L, eventTime, action, x, y, 0)
    }

    private companion object {
        const val FLING_DURATION_MS = 100L

        /** Large enough to contain every in-viewport coordinate the tests send. */
        const val VIEWPORT_SIZE = 1000
    }
}
