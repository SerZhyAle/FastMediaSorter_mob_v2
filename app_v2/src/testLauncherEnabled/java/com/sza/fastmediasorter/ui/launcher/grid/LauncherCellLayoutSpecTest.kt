package com.sza.fastmediasorter.ui.launcher.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4475: the shortcut caption and section title fit arithmetic of [LauncherGridGeometry].
 *
 * S1757 scaled a shortcut cell by its size alone and its test asserted only that the icon got smaller, so
 * a caption whose second line did not fit shipped: the line carrying the ellipsis was cut by the cell and
 * the label read as hard-clipped. Every assertion here is about the sum, not about one number - whether
 * the icon, the paddings and the allowed caption lines add up to a height the cell has.
 *
 * Kept apart from [LauncherGridGeometryTest], which is at detekt's function-count ceiling for a class.
 */
class LauncherCellLayoutSpecTest {

    @Test
    fun `a normal cell keeps two caption lines at the layout defaults`() {
        val spec = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = 96f)
        assertEquals(2, spec.labelMaxLines)
        assertEquals(10f, spec.labelTextSizeSp, TOLERANCE)
        assertEquals(4f, spec.contentPaddingHorizontalDp, TOLERANCE)
        assertEquals(4f, spec.labelPlatePaddingHorizontalDp, TOLERANCE)
        assertEquals(1f, spec.labelPlatePaddingVerticalDp, TOLERANCE)
    }

    @Test
    fun `a cell with no measured size falls back to the layout defaults`() {
        val spec = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = 0f)
        assertEquals(44f, spec.iconSizeDp, TOLERANCE)
        assertEquals(2, spec.labelMaxLines)
        assertEquals(10f, spec.labelTextSizeSp, TOLERANCE)
    }

    @Test
    fun `a dense cell keeps two caption lines and the whole content fits the cell`() {
        // 48dp is the real cell on a 411dp phone at the densest factor once a taskbar takes the right side.
        listOf(48f, 56f, 64f, 72f, 80f).forEach { cellDp ->
            val spec = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = cellDp, fontScale = 1f)
            assertEquals("lines at ${cellDp}dp", 2, spec.labelMaxLines)
            assertTrue(
                "content ${spec.contentHeightDp(1f)}dp must fit the ${cellDp}dp cell",
                spec.contentHeightDp(1f) <= cellDp + FIT_EPSILON,
            )
        }
    }

    @Test
    fun `a moderately enlarged font still fits two lines at 64dp`() {
        val spec = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = 64f, fontScale = 1.15f)
        assertEquals(2, spec.labelMaxLines)
        assertTrue(spec.contentHeightDp(1.15f) <= 64f + FIT_EPSILON)
        assertTrue(spec.iconSizeDp >= 20f)
    }

    @Test
    fun `an enlarged font on a small cell drops to one line instead of clipping the second`() {
        val spec = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = 48f, fontScale = 1.3f)
        assertEquals(1, spec.labelMaxLines)
        assertTrue(spec.contentHeightDp(1.3f) <= 48f + FIT_EPSILON)
    }

    @Test
    fun `the caption shrinks and its paddings tighten with the cell but never past their floors`() {
        val dense = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = 48f)
        val loose = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = 80f)
        assertTrue(dense.labelTextSizeSp < loose.labelTextSizeSp)
        assertTrue(dense.labelTextSizeSp >= 8f)
        assertTrue(dense.contentPaddingHorizontalDp < loose.contentPaddingHorizontalDp)
        assertTrue(dense.contentPaddingHorizontalDp >= 2f)
        assertTrue(dense.labelPlatePaddingHorizontalDp >= 2f)
        assertTrue(dense.labelPlatePaddingVerticalDp >= 0.5f)
    }

    @Test
    fun `a non-positive font scale is read as 1 rather than collapsing the fit`() {
        val broken = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = 64f, fontScale = 0f)
        val normal = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = 64f, fontScale = 1f)
        assertEquals(normal, broken)
    }

    @Test
    fun `a section header at normal size keeps the layout defaults`() {
        val spec = LauncherGridGeometry.sectionHeaderLayoutSpec(cellSizeDp = 96f)
        assertEquals(24f, spec.chevronSizeDp, TOLERANCE)
        assertEquals(4f, spec.rootInsetHorizontalDp, TOLERANCE)
        assertEquals(4f, spec.stripPaddingHorizontalDp, TOLERANCE)
        assertEquals(14f, spec.titleMaxTextSizeSp, TOLERANCE)
    }

    @Test
    fun `a section header gives width back in a dense grid and respects its floors`() {
        val spec = LauncherGridGeometry.sectionHeaderLayoutSpec(cellSizeDp = 48f)
        assertTrue(spec.chevronSizeDp < 24f)
        assertTrue(spec.chevronSizeDp >= 16f)
        assertTrue(spec.rootInsetHorizontalDp >= 2f)
        assertTrue(spec.stripPaddingHorizontalDp >= 2f)
        assertTrue(spec.titleMinTextSizeSp < spec.titleMaxTextSizeSp)
    }

    private companion object {
        const val TOLERANCE = 0.01f

        /** Float rounding only; a real overflow of the cell is a whole dp or more. */
        const val FIT_EPSILON = 0.05f
    }
}
