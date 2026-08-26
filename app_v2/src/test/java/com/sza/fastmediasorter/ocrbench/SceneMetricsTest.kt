package com.sza.fastmediasorter.ocrbench

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1716: the four rectangle axes, and the three ways a scene ends up with no number at all.
 *
 * Every assertion below is arithmetic on boxes. There is no bitmap comparison anywhere on purpose:
 * strategic §6.1 measured that this host's Robolectric records draw calls instead of rasterising, so
 * a pixel assertion would pass on an empty canvas - the silent zero the whole ticket exists to make
 * impossible. The pixel axis lives in S1782.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SceneMetricsTest {

    @Test
    fun `a plate covering its whole line scores full overlap`() {
        val annotation = annotation(textAreas = listOf(LINE), paintable = listOf(WIDE_PAPER))

        val metrics = SceneScorer.score(annotation, run(annotation, block(LINE)))

        assertEquals(1.0, value(metrics.found), DELTA)
        assertEquals(1.0, value(metrics.overlap), DELTA)
        assertEquals(0.0, value(metrics.spill), DELTA)
    }

    @Test
    fun `a line left unplated halves both found and overlap`() {
        val annotation = annotation(
            textAreas = listOf(LINE, SECOND_LINE),
            paintable = listOf(WIDE_PAPER),
        )

        val metrics = SceneScorer.score(annotation, run(annotation, block(LINE)))

        assertEquals(0.5, value(metrics.found), DELTA)
        assertEquals(0.5, value(metrics.overlap), DELTA)
    }

    @Test
    fun `a plate wider than its paper is scored as spill`() {
        val annotation = annotation(textAreas = listOf(LINE), paintable = listOf(LINE))

        val metrics = SceneScorer.score(
            annotation,
            run(annotation, block(LINE, translationWidth = 200f, padding = 5f)),
        )

        // Plate 210 x 20 against 100 x 20 of allowed paper: 2200 of 4200 pixels land on the picture.
        assertEquals(2200.0 / 4200.0, value(metrics.spill), DELTA)
    }

    @Test
    fun `a draft annotation is refused and stays unmeasured`() {
        val annotation = annotation(textAreas = listOf(LINE), paintable = listOf(WIDE_PAPER), draft = true)

        val failure = assertThrows(OverlayRectangleRun.RunFailure::class.java) {
            run(annotation, block(LINE))
        }

        assertTrue(failure.message.orEmpty().contains("draft"))
        val metrics = SceneScorer.unmeasured(annotation.sceneId, "draft annotation")
        assertTrue(metrics.overlap is Measured.Unmeasured)
        assertTrue(metrics.spill is Measured.Unmeasured)
    }

    @Test
    fun `an empty recognised result is a run failure, not a perfect score`() {
        val annotation = annotation(textAreas = listOf(LINE), paintable = listOf(WIDE_PAPER))

        val failure = assertThrows(OverlayRectangleRun.RunFailure::class.java) {
            OverlayRectangleRun.run(annotation, emptyList(), VIEW_BOTTOM) { 0L }
        }

        assertTrue(failure.message.orEmpty().contains("no block"))
        assertTrue(SceneScorer.unmeasured(annotation.sceneId, "no block").found is Measured.Unmeasured)
    }

    @Test
    fun `an unreadable scene is refused and stays unmeasured`() {
        val annotation = annotation(
            textAreas = listOf(LINE),
            paintable = listOf(WIDE_PAPER),
            readable = false,
        )

        val failure = assertThrows(OverlayRectangleRun.RunFailure::class.java) {
            run(annotation, block(LINE))
        }

        assertTrue(failure.message.orEmpty().contains("unreadable"))
    }

    @Test
    fun `a summary counts the unmeasured scene apart from the measured one`() {
        val annotation = annotation(textAreas = listOf(LINE), paintable = listOf(WIDE_PAPER))
        val measured = SceneScorer.score(annotation, run(annotation, block(LINE)))
        val missing = SceneScorer.unmeasured("no-annotation", "the scene carries no annotation")

        val summary = CorpusSummary.of(listOf(measured, missing))

        assertEquals(2, summary.sceneCount)
        val overlap = summary.axis(CorpusSummary.AXIS_OVERLAP)
        assertEquals(1, overlap.measuredCount)
        assertEquals(1, overlap.unmeasuredCount)
        assertEquals(1.0, requireNotNull(overlap.worst), DELTA)
        assertEquals(1.0, requireNotNull(overlap.median), DELTA)
        assertTrue(overlap.unmeasuredReasons.contains("the scene carries no annotation"))
    }

    @Test
    fun `a duration is measured even when nothing else can be`() {
        val annotation = annotation(textAreas = listOf(LINE), paintable = listOf(WIDE_PAPER))

        val result = OverlayRectangleRun.run(
            annotation,
            listOf(block(LINE)),
            VIEW_BOTTOM,
            nanoClock = FixedClock(),
        )

        assertEquals(ELAPSED_NANOS, result.durationNanos)
    }

    /** Two readings, so the run's own subtraction is what the assertion sees. */
    private class FixedClock : () -> Long {
        private var call = 0
        override fun invoke(): Long {
            call++
            return if (call == 1) 0L else ELAPSED_NANOS
        }
    }

    private fun run(
        annotation: SceneAnnotation,
        vararg blocks: OverlayRectangleRun.TranslatedBlock,
    ): OverlayRectangleRun.RectangleRunResult =
        OverlayRectangleRun.run(annotation, blocks.toList(), VIEW_BOTTOM) { 0L }

    private fun block(
        box: Rect,
        translationWidth: Float = 10f,
        translationHeight: Float = 5f,
        padding: Float = 2f,
    ) = OverlayRectangleRun.TranslatedBlock(box, translationWidth, translationHeight, padding)

    private fun annotation(
        textAreas: List<Rect>,
        paintable: List<Rect>,
        draft: Boolean = false,
        readable: Boolean = true,
    ) = SceneAnnotation(
        version = SceneAnnotation.CURRENT_VERSION,
        sceneId = "unit-scene",
        widthPx = SCENE_WIDTH,
        heightPx = SCENE_HEIGHT,
        textAreas = textAreas.map { TextArea("text", it) },
        paintableAreas = paintable.map { PaintableArea(it) },
        readable = readable,
        provenance = Provenance(author = "unit test", annotatedOn = "2026-08-25", draft = draft),
    )

    private fun value(measured: Measured<Double>): Double = when (measured) {
        is Measured.Value -> measured.value
        is Measured.Unmeasured -> error("expected a measured value, got: ${measured.reason}")
    }

    private companion object {
        val LINE = Rect(0, 0, 100, 20)
        val SECOND_LINE = Rect(0, 40, 100, 60)
        val WIDE_PAPER = Rect(0, 0, 400, 200)
        const val SCENE_WIDTH = 400
        const val SCENE_HEIGHT = 200
        const val VIEW_BOTTOM = 200f
        const val ELAPSED_NANOS = 4242L
        const val DELTA = 0.0001
    }
}
