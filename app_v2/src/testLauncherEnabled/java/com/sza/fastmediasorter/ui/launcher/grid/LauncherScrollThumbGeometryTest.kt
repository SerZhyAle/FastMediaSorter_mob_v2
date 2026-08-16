package com.sza.fastmediasorter.ui.launcher.grid

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1430: the mapping between a touch on the thumb and a scroll position is the only half of "the thumb and
 * the content stay in step" (strategic section 11 criteria 1-2) provable without a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherScrollThumbGeometryTest {

    private fun thumb(viewHeightPx: Int = TRACK_HEIGHT): LauncherScrollThumbView {
        val view = LauncherScrollThumbView(RuntimeEnvironment.getApplication())
        view.measure(
            View.MeasureSpec.makeMeasureSpec(TOUCH_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(viewHeightPx, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, TOUCH_WIDTH, viewHeightPx)
        return view
    }

    @Test
    fun `a touch at the top scrolls to the start`() {
        val view = thumb()
        view.onScrollPositionChanged(scrollY = 0, contentHeight = 4000, viewportHeight = 1000)

        assertEquals(0, view.scrollOffsetForTouch(0f))
    }

    @Test
    fun `a touch at the bottom scrolls to the end`() {
        val view = thumb()
        view.onScrollPositionChanged(scrollY = 0, contentHeight = 4000, viewportHeight = 1000)

        assertEquals(3000, view.scrollOffsetForTouch(TRACK_HEIGHT.toFloat()))
    }

    @Test
    fun `a touch in the middle lands mid content`() {
        val view = thumb()
        view.onScrollPositionChanged(scrollY = 0, contentHeight = 4000, viewportHeight = 1000)

        val middle = view.scrollOffsetForTouch(TRACK_HEIGHT / 2f)

        assertTrue("expected mid-content, got $middle", middle in 1400..1600)
    }

    @Test
    fun `content shorter than the viewport is not scrollable and yields no offset`() {
        val view = thumb()
        view.onScrollPositionChanged(scrollY = 0, contentHeight = 800, viewportHeight = 1000)

        assertFalse(view.isScrollable())
        assertEquals(0, view.scrollOffsetForTouch(TRACK_HEIGHT / 2f))
        assertEquals(0, view.thumbHeightPx())
    }

    @Test
    fun `a very long desktop still leaves a grabbable thumb`() {
        val view = thumb()
        view.onScrollPositionChanged(scrollY = 0, contentHeight = 400_000, viewportHeight = 1000)

        assertTrue("thumb collapsed to ${view.thumbHeightPx()}px", view.thumbHeightPx() > 0)
        assertTrue(view.thumbHeightPx() < TRACK_HEIGHT)
    }

    @Test
    fun `the thumb sits at the bottom when the content is scrolled to the end`() {
        val view = thumb()
        view.onScrollPositionChanged(scrollY = 3000, contentHeight = 4000, viewportHeight = 1000)

        assertEquals(TRACK_HEIGHT - view.thumbHeightPx(), view.thumbTopPx())
    }

    private companion object {
        const val TRACK_HEIGHT = 600
        const val TOUCH_WIDTH = 72
    }
}
