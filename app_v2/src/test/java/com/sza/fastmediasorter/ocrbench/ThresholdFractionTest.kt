package com.sza.fastmediasorter.ocrbench

import android.graphics.Rect
import com.sza.fastmediasorter.domain.ocr.OcrBlockFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S2036: the fraction the ADR-3 verdict is read from.
 *
 * `the fraction falls when the line grows` is the load-bearing case: an inverted divisor would still
 * produce a plausible-looking fraction, it would simply point the wrong way, and the ADR would then be
 * decided from it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThresholdFractionTest {

    @Test
    fun `the fractions are the live thresholds over the median line height`() {
        // Line heights 40 and 60 -> median 50.
        val scene = sceneOf(lineHeights = listOf(40, 60))

        val fraction = ThresholdFraction.of(scene)

        assertEquals(50.0, fraction.medianLineHeight.valueOrFail(), EPSILON)
        assertEquals(OcrBlockFilter.MIN_BOX_WIDTH / 50.0, fraction.widthFraction.valueOrFail(), EPSILON)
        assertEquals(OcrBlockFilter.MIN_BOX_HEIGHT / 50.0, fraction.heightFraction.valueOrFail(), EPSILON)
    }

    @Test
    fun `the fraction falls when the line grows and the resolution does not`() {
        val short = ThresholdFraction.of(sceneOf(lineHeights = listOf(20)))
        val tall = ThresholdFraction.of(sceneOf(lineHeights = listOf(80)))

        assertTrue(
            "a taller line must make the same absolute threshold a smaller fraction of it",
            tall.heightFraction.valueOrFail() < short.heightFraction.valueOrFail()
        )
        assertEquals(short.widthPx, tall.widthPx)
    }

    @Test
    fun `a scene with no non-empty text area is unmeasured`() {
        val scene = sceneOf(lineHeights = listOf(0))

        val fraction = ThresholdFraction.of(scene)

        assertEquals(Measured.Unmeasured(ThresholdFraction.NO_TEXT_AREA), fraction.medianLineHeight)
        assertEquals(Measured.Unmeasured(ThresholdFraction.NO_TEXT_AREA), fraction.heightFraction)
        assertEquals("the resolution is known even when nothing else is", SCENE_WIDTH, fraction.widthPx)
    }

    @Test
    fun `one measured scene yields no spread rather than a zero one`() {
        val single = listOf(ThresholdFraction.of(sceneOf(lineHeights = listOf(40))))

        assertNull(ThresholdFraction.heightFractionSpread(single))
    }

    /**
     * The case the whole corpus currently sits in: several scenes, differing line heights, one
     * resolution. A zero here would be read as "the fraction is stable", which is a finding nobody made.
     */
    @Test
    fun `scenes sharing one resolution yield no spread however many there are`() {
        val fractions = listOf(20, 40, 80).map { ThresholdFraction.of(sceneOf(lineHeights = listOf(it))) }

        assertNull(
            "one resolution can only produce a spread by construction",
            ThresholdFraction.heightFractionSpread(fractions)
        )
    }

    @Test
    fun `two resolutions do yield a spread`() {
        val fractions = listOf(
            ThresholdFraction.of(sceneOf(lineHeights = listOf(20))),
            ThresholdFraction.of(sceneOf(lineHeights = listOf(80), width = SCENE_WIDTH * 2, height = SCENE_HEIGHT * 2)),
        )

        val spread = ThresholdFraction.heightFractionSpread(fractions)

        assertEquals(OcrBlockFilter.MIN_BOX_HEIGHT / 20.0 - OcrBlockFilter.MIN_BOX_HEIGHT / 80.0, spread!!, EPSILON)
    }

    @Test
    fun `an unmeasured scene never enters the spread`() {
        val fractions = listOf(
            ThresholdFraction.of(sceneOf(lineHeights = listOf(40))),
            ThresholdFraction.of(sceneOf(lineHeights = listOf(0), width = SCENE_WIDTH * 2, height = SCENE_HEIGHT * 2)),
        )

        assertNull(
            "the second scene is unmeasured, so only one resolution was actually measured",
            ThresholdFraction.heightFractionSpread(fractions)
        )
    }

    private fun sceneOf(
        lineHeights: List<Int>,
        width: Int = SCENE_WIDTH,
        height: Int = SCENE_HEIGHT,
    ): SceneAnnotation {
        val areas = lineHeights.mapIndexed { index, lineHeight ->
            val top = index * LINE_PITCH
            TextArea("line $index", Rect(0, top, LINE_WIDTH, top + lineHeight))
        }
        return SceneAnnotation(
            version = SceneAnnotation.CURRENT_VERSION,
            sceneId = "threshold-fixture",
            widthPx = width,
            heightPx = height,
            textAreas = areas,
            paintableAreas = areas.map { PaintableArea(it.box) },
            readable = true,
            provenance = Provenance("S2036 test", "2026-08-26", draft = false),
        )
    }

    private fun Measured<Double>.valueOrFail(): Double = when (this) {
        is Measured.Value -> value
        is Measured.Unmeasured -> throw AssertionError("expected a measured value, got: $reason")
    }

    private companion object {
        const val EPSILON = 1e-9
        const val LINE_WIDTH = 300
        const val LINE_PITCH = 100
        const val SCENE_WIDTH = 800
        const val SCENE_HEIGHT = 600
    }
}
