package com.sza.fastmediasorter.ocrbench

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S2036: the two relations, and above all the fact that they are two.
 *
 * The decisive case is `median and maximum are aggregated to different lines` - it is the only test here
 * that fails if the two aggregations are swapped. Every other case passes either way, because on a scene
 * whose lines agree the median and the maximum coincide, and a swapped implementation would ship looking
 * measured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeightRelationTest {

    @Test
    fun `both relations are the arithmetic they claim`() {
        // Words 20 and 40 high -> median 30. Line box 60 high -> lineToWord 2.0, wordToMedian 40/30.
        val scene = sceneOf(line(lineHeight = 60, wordHeights = listOf(20, 40)))

        val relation = HeightRelation.of(scene)

        assertEquals(2.0, relation.lineToWord.valueOrFail(), EPSILON)
        assertEquals(40.0 / 30.0, relation.wordToMedian.valueOrFail(), EPSILON)
    }

    @Test
    fun `median and maximum are aggregated to different lines`() {
        // Three words a line, so each line's median is a real word height rather than a midpoint.
        // Per line lineToWord:   3.0, 4.0, 5.0 -> median 4.0 but maximum 5.0.
        // Per line wordToMedian: 1.0, 1.0, 2.0 -> median 1.0 but maximum 2.0.
        val scene = sceneOf(
            line(lineHeight = 30, wordHeights = listOf(10, 10, 10)),
            line(lineHeight = 40, wordHeights = listOf(10, 10, 10)),
            line(lineHeight = 50, wordHeights = listOf(10, 10, 20)),
        )

        val relation = HeightRelation.of(scene)

        assertEquals("lineToWord must be the median across lines", 4.0, relation.lineToWord.valueOrFail(), EPSILON)
        assertEquals("wordToMedian must be the maximum across lines", 2.0, relation.wordToMedian.valueOrFail(), EPSILON)
    }

    @Test
    fun `a scene with no annotated word is unmeasured, not zero`() {
        val scene = sceneOf(TextArea("Hello world", Rect(0, 0, 300, 48)))

        val relation = HeightRelation.of(scene)

        assertEquals(Measured.Unmeasured(HeightRelation.NO_WORDS), relation.lineToWord)
        assertEquals(Measured.Unmeasured(HeightRelation.NO_WORDS), relation.wordToMedian)
    }

    @Test
    fun `a zero-height word refuses instead of dividing`() {
        val scene = sceneOf(line(lineHeight = 48, wordHeights = listOf(0, 0)))

        val relation = HeightRelation.of(scene)

        assertEquals(Measured.Unmeasured(HeightRelation.ZERO_MEDIAN), relation.lineToWord)
        assertEquals(Measured.Unmeasured(HeightRelation.ZERO_MEDIAN), relation.wordToMedian)
    }

    @Test
    fun `an unscorable annotation is refused before any arithmetic`() {
        val base = sceneOf(line(lineHeight = 60, wordHeights = listOf(20, 40)))
        val draft = base.copy(provenance = base.provenance.copy(draft = true))

        assertEquals(Measured.Unmeasured(HeightRelation.NOT_SCORABLE), HeightRelation.of(draft).lineToWord)
    }

    @Test
    fun `a line without words is skipped rather than dragging the scene down`() {
        val scene = sceneOf(
            TextArea("not annotated", Rect(0, 0, 300, 48)),
            line(lineHeight = 60, wordHeights = listOf(20, 40)),
        )

        assertEquals(2.0, HeightRelation.of(scene).lineToWord.valueOrFail(), EPSILON)
    }

    @Test
    fun `the synthetic varied-height scene measures both relations`() {
        val scene = SyntheticScene.lineWithVariedWordHeights().annotation

        val relation = HeightRelation.of(scene)

        // Authored heights 30, 26, 18, 40 -> median 28; line box is one LINE_HEIGHT of 48.
        assertEquals(48.0 / 28.0, relation.lineToWord.valueOrFail(), EPSILON)
        assertEquals(40.0 / 28.0, relation.wordToMedian.valueOrFail(), EPSILON)
        assertTrue(
            "the two relations must differ, or this scene proves nothing about keeping them apart",
            relation.lineToWord.valueOrFail() != relation.wordToMedian.valueOrFail()
        )
    }

    private fun line(lineHeight: Int, wordHeights: List<Int>): TextArea {
        val words = wordHeights.mapIndexed { index, height ->
            val left = index * WORD_PITCH
            AnnotatedWord("w$index", Rect(left, 0, left + WORD_WIDTH, height))
        }
        return TextArea(wordHeights.indices.joinToString(" ") { "w$it" }, Rect(0, 0, LINE_WIDTH, lineHeight), words)
    }

    private fun sceneOf(vararg areas: TextArea): SceneAnnotation = SceneAnnotation(
        version = SceneAnnotation.CURRENT_VERSION,
        sceneId = "height-relation-fixture",
        widthPx = SCENE_WIDTH,
        heightPx = SCENE_HEIGHT,
        textAreas = areas.toList(),
        paintableAreas = areas.map { PaintableArea(it.box) },
        readable = true,
        provenance = Provenance("S2036 test", "2026-08-26", draft = false),
    )

    private fun Measured<Double>.valueOrFail(): Double = when (this) {
        is Measured.Value -> value
        is Measured.Unmeasured -> throw AssertionError("expected a measured value, got: $reason")
    }

    private companion object {
        const val EPSILON = 1e-9
        const val LINE_WIDTH = 300
        const val WORD_WIDTH = 40
        const val WORD_PITCH = 50
        const val SCENE_WIDTH = 800
        const val SCENE_HEIGHT = 600
    }
}
