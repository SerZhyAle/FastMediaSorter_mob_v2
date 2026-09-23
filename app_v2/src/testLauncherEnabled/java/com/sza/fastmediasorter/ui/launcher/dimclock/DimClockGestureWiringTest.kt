package com.sza.fastmediasorter.ui.launcher.dimclock

import android.app.Application
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockInteractionHandler
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockOverlayView
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyle
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyleProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusContentProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * S3366 gesture follow-up: the dim clock block must route real touch streams - dispatched through
 * the actual view hierarchy, exactly as the window delivers them - to the interaction handler. This
 * test exists because a binding that looks right and a stream that reaches the handler are different
 * claims; it dispatches DOWN/MOVE/UP sequences at the view and counts what the handler receives.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DimClockGestureWiringTest {

    private lateinit var application: Application
    private lateinit var handler: RecordingHandler
    private lateinit var rootView: FrameLayout

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        handler = RecordingHandler()
        rootView = FrameLayout(application)
        val clockView = DimClockOverlayView(application)
        clockView.bind(
            FakeStyleProvider(),
            FakeStatusProvider(),
            interactionHandler = handler,
        )
        rootView.addView(
            clockView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        val width = 600
        val height = 400
        rootView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        rootView.layout(0, 0, width, height)
    }

    @Test
    fun `a tap stream on the clock reaches the handler exactly once`() {
        val block = findBlock()
        val x = block.width / 2f
        val y = block.height / 2f

        block.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, y, atMs = 0))
        block.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x, y, atMs = 40))
        idleFor(TAP_TIMEOUT_MS)

        assertEquals(1, handler.tapCount)
    }

    @Test
    fun `a horizontal fling reaches the handler with its metrics`() {
        val block = findBlock()
        val startX = block.width / 2f
        val startY = block.height / 2f

        block.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, startX, startY, atMs = 0))
        var currentX = startX
        var atMs = 0L
        for (move in 1..FLING_MOVES) {
            atMs = move * FLING_STEP_MS
            currentX -= FLING_STEP_PX
            block.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, currentX, startY, atMs = atMs))
        }
        block.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, currentX, startY, atMs = atMs + FLING_STEP_MS))
        idleFor(TAP_TIMEOUT_MS)

        assertEquals(1, handler.flingCount)
        assertTrue(handler.lastFlingDistanceX < 0f)
    }

    @Test
    fun `a long press reaches the handler`() {
        val block = findBlock()
        val x = block.width / 2f
        val y = block.height / 2f

        block.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, y, atMs = 0))
        // The long-press timer lives on the looper, which runs during a real hold; advance it here
        // before the UP, or the detector cancels a timer that never got to fire.
        idleFor(LONG_PRESS_TIMEOUT_MS + 100)
        block.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x, y, atMs = LONG_PRESS_TIMEOUT_MS + 150))
        idleFor(TAP_TIMEOUT_MS)

        assertEquals(1, handler.longPressCount)
    }

    private fun findBlock(): View {
        val clockView = rootView.getChildAt(0) as DimClockOverlayView
        val block = clockView.findViewById<View>(R.id.dimClockBlock)
        check(block != null && block.width > 0) { "dimClockBlock missing or not laid out" }
        return block
    }

    private fun motion(action: Int, x: Float, y: Float, atMs: Long): MotionEvent =
        MotionEvent.obtain(0, atMs, action, x, y, 0)

    private fun idleFor(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms))
    }

    private class RecordingHandler : DimClockInteractionHandler {
        var tapCount = 0
        var longPressCount = 0
        var flingCount = 0
        var lastFlingDistanceX = Float.NaN

        override fun onFling(
            distanceX: Float,
            distanceY: Float,
            velocityX: Float,
            velocityY: Float,
            touchSlop: Float,
            minimumFlingVelocity: Float,
        ): Boolean {
            flingCount++
            lastFlingDistanceX = distanceX
            return true
        }

        override fun onClockTap(dismissDim: () -> Unit): Boolean {
            tapCount++
            return true
        }

        override fun onClockLongPress(dismissDim: () -> Unit): Boolean {
            longPressCount++
            return true
        }
    }

    private class FakeStyleProvider : DimClockStyleProvider {
        override fun getStyle(): DimClockStyle =
            DimClockStyle(dialColor = null, dialTypeface = "default", secondsVisible = true)

        override val secondsVisible: Boolean = true
    }

    private class FakeStatusProvider : DimStatusContentProvider {
        override fun observeStatus(): Flow<DimStatusSnapshot> = flowOf(DimStatusSnapshot())
    }

    private companion object {
        const val TAP_TIMEOUT_MS = 400L
        const val LONG_PRESS_TIMEOUT_MS = 600L
        const val FLING_MOVES = 8
        const val FLING_STEP_MS = 16L
        const val FLING_STEP_PX = 25f
    }
}
