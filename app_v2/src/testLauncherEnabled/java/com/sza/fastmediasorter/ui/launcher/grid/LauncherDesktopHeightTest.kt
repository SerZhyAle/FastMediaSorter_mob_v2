package com.sza.fastmediasorter.ui.launcher.grid

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S2660: the canvas is measured from the rows its content occupies, never from the rows it addresses.
 *
 * [LauncherDesktopLayout.rows] is floored to the ceiling-rounded viewport (S1288, S2387) so that a long
 * press below the last shortcut still resolves to a slot. Taking the height from that floor made the
 * canvas taller than the screen by the rounding remainder on every desktop, and the scroll container
 * turned the remainder into real travel - the thumb showed itself with nothing to scroll, and a vertical
 * swipe was eaten before the desktop gesture saw it. These cases hold the two apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherDesktopHeightTest {

    private companion object {
        const val COLUMNS = 4
        const val WIDTH = 400
        const val CELL = WIDTH / COLUMNS

        /** Deliberately not a whole number of cells - the remainder is what used to leak into travel. */
        const val VIEWPORT = CELL * 5 + 40

        /** What the binder addresses at rest: the viewport rounded UP to whole rows. */
        const val ADDRESSED_ROWS = 6

        const val OCCUPIED_ROWS = 2
        const val SINGLE_ROW = 1
        const val TALL_ROWS = 9
    }

    private fun measuredHeight(rows: Int, contentRows: Int): Int {
        val layout = LauncherDesktopLayout(RuntimeEnvironment.getApplication())
        layout.columns = COLUMNS
        layout.rows = rows
        layout.contentRows = contentRows
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(VIEWPORT, View.MeasureSpec.EXACTLY),
        )
        return layout.measuredHeight
    }

    @Test
    fun `a desktop that fits measures to the viewport, leaving nothing to scroll`() {
        assertEquals(VIEWPORT, measuredHeight(rows = ADDRESSED_ROWS, contentRows = OCCUPIED_ROWS))
    }

    @Test
    fun `the addressed rows do not lengthen a desktop that fits`() {
        assertEquals(
            measuredHeight(rows = OCCUPIED_ROWS, contentRows = OCCUPIED_ROWS),
            measuredHeight(rows = ADDRESSED_ROWS, contentRows = OCCUPIED_ROWS),
        )
    }

    @Test
    fun `a single occupied row still fills the viewport, so a press below it lands on the canvas`() {
        assertEquals(VIEWPORT, measuredHeight(rows = ADDRESSED_ROWS, contentRows = SINGLE_ROW))
    }

    @Test
    fun `a desktop taller than the viewport measures to its own content`() {
        assertEquals(CELL * TALL_ROWS, measuredHeight(rows = TALL_ROWS, contentRows = TALL_ROWS))
    }
}
