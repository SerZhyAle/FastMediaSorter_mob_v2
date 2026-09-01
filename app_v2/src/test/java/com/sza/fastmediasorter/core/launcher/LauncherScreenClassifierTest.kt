package com.sza.fastmediasorter.core.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2309: [LauncherScreenClassifier] is the only place the strategic ADR-6 thresholds are written
 * down, so each boundary is asserted on both sides - a future change to one of them has to change a
 * test rather than pass silently.
 *
 * Size boundaries under test: 599, 600, 839, 840. Ratio boundaries under test: 1.49, 1.5, 1.9, 1.91.
 * Ratios are expressed as dp pairs over a hundred so the intended value is exact in the source.
 */
@Suppress("FunctionNaming") // backtick test names, project convention (cf. LauncherStarterSetsTest)
class LauncherScreenClassifierTest {

    @Test
    fun `tall phone is compact and elongated`() {
        val actual = LauncherScreenClassifier.classify(smallestWidthDp = 392, screenWidthDp = 392, screenHeightDp = 871)

        assertEquals(LauncherScreenClass.Size.COMPACT, actual.size)
        assertEquals(LauncherScreenClass.Shape.ELONGATED, actual.shape)
    }

    @Test
    fun `head unit is medium and wide`() {
        val actual =
            LauncherScreenClassifier.classify(smallestWidthDp = 600, screenWidthDp = 1067, screenHeightDp = 600)

        assertEquals(LauncherScreenClass.Size.MEDIUM, actual.size)
        assertEquals(LauncherScreenClass.Shape.WIDE, actual.shape)
    }

    @Test
    fun `four by three tablet is expanded and balanced`() {
        val actual =
            LauncherScreenClassifier.classify(smallestWidthDp = 900, screenWidthDp = 1200, screenHeightDp = 900)

        assertEquals(LauncherScreenClass.Size.EXPANDED, actual.size)
        assertEquals(LauncherScreenClass.Shape.BALANCED, actual.shape)
    }

    @Test
    fun `size boundaries fall on the documented side`() {
        assertEquals(LauncherScreenClass.Size.COMPACT, sizeOf(599))
        assertEquals(LauncherScreenClass.Size.MEDIUM, sizeOf(600))
        assertEquals(LauncherScreenClass.Size.MEDIUM, sizeOf(839))
        assertEquals(LauncherScreenClass.Size.EXPANDED, sizeOf(840))
    }

    @Test
    fun `ratio boundaries fall on the documented side`() {
        assertEquals(LauncherScreenClass.Shape.BALANCED, shapeOf(149))
        assertEquals(LauncherScreenClass.Shape.WIDE, shapeOf(150))
        assertEquals(LauncherScreenClass.Shape.WIDE, shapeOf(190))
        assertEquals(LauncherScreenClass.Shape.ELONGATED, shapeOf(191))
    }

    @Test
    fun `swapping the two dimensions changes nothing`() {
        val portrait = LauncherScreenClassifier.classify(392, 392, 871)
        val landscape = LauncherScreenClassifier.classify(392, 871, 392)

        assertEquals(portrait, landscape)
    }

    @Test
    fun `unresolved configuration falls back to the smallest class`() {
        val actual = LauncherScreenClassifier.classify(smallestWidthDp = 900, screenWidthDp = 0, screenHeightDp = 0)

        assertEquals(LauncherScreenClass.Size.COMPACT, actual.size)
        assertEquals(LauncherScreenClass.Shape.BALANCED, actual.shape)
    }

    private fun sizeOf(smallestWidthDp: Int): LauncherScreenClass.Size =
        LauncherScreenClassifier.classify(smallestWidthDp, smallestWidthDp, smallestWidthDp).size

    private fun shapeOf(longSideDp: Int): LauncherScreenClass.Shape =
        LauncherScreenClassifier.classify(SHORT_SIDE_DP, longSideDp, SHORT_SIDE_DP).shape

    private companion object {
        /** Long side over this gives the ratio directly, so a test reads its own boundary value. */
        const val SHORT_SIDE_DP = 100
    }
}
