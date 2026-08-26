package com.sza.fastmediasorter.ocrbench

import android.graphics.Rect
import com.sza.fastmediasorter.domain.ocr.OverlayPlateBounds
import kotlin.math.roundToInt

/**
 * S1716: a number that knows whether it is a number.
 *
 * The whole ticket exists because a metric that cannot be computed used to arrive as a zero, and a
 * zero reads as "measured, and bad" - or worse, as "measured, and perfect". [Unmeasured] carries the
 * reason instead, and the summary counts it separately (strategic §2.4, §5.1 pillar 4).
 */
sealed interface Measured<out T> {

    /** A value that was actually computed from the scene. */
    data class Value<out T>(val value: T) : Measured<T>

    /** No value exists, and why. Never substitute a default for this. */
    data class Unmeasured(val reason: String) : Measured<Nothing>
}

/**
 * The four rectangle-only axes of this ticket, scored for one scene.
 *
 * There is deliberately no concealment or pixel-damage axis: it needs a rasterised composition of
 * plate over source, which the owner moved to S1782 on 2026-08-17 together with the Robolectric
 * upgrade it really costs. An empty field here would read as "measured and fine", so the axis is
 * absent rather than present-and-zero.
 */
data class SceneMetrics(
    val sceneId: String,
    /** Fraction of annotated text areas that at least one plate touches. */
    val found: Measured<Double>,
    /** Fraction of the annotated text area that the plates actually cover. */
    val overlap: Measured<Double>,
    /** Fraction of the plate area falling outside every area a plate was allowed to paint. */
    val spill: Measured<Double>,
    val durationNanos: Measured<Long>,
    /** S2036: line box height over the median word height, median across the scene's annotated lines. */
    val lineToWord: Measured<Double> = Measured.Unmeasured(HeightRelation.NO_WORDS),
    /** S2036: tallest word over its line's median word, maximum across the scene's annotated lines. */
    val wordToMedian: Measured<Double> = Measured.Unmeasured(HeightRelation.NO_WORDS),
)

/** Scores one run against the annotation it was run on. */
object SceneScorer {

    /** Every axis unmeasured for the same reason - what a failed or refused run scores. */
    fun unmeasured(sceneId: String, reason: String): SceneMetrics {
        val absent = Measured.Unmeasured(reason)
        return SceneMetrics(sceneId, absent, absent, absent, absent, absent, absent)
    }

    fun score(
        annotation: SceneAnnotation,
        result: OverlayRectangleRun.RectangleRunResult,
    ): SceneMetrics {
        if (!annotation.isScorable()) {
            return unmeasured(annotation.sceneId, "the annotation is not scorable")
        }
        val plates = result.plates.map(::toRect).filter { !it.isEmpty }
        val textAreas = annotation.textAreas.map { it.box }.filter { !it.isEmpty }
        val paintable = annotation.paintableAreas.map { it.box }.filter { !it.isEmpty }
        val plateArea = RectCoverage.coveredArea(plates, null)
        // Read from the annotation alone: both relations are properties of the material, not of the
        // plate arithmetic, so routing them through the run result would make a material number move
        // whenever the geometry changed (S2036 ADR-1).
        val heights = HeightRelation.of(annotation)

        return SceneMetrics(
            sceneId = annotation.sceneId,
            found = foundRatio(plates, textAreas),
            overlap = overlapRatio(plates, textAreas),
            spill = spillRatio(plates, paintable, plateArea),
            durationNanos = Measured.Value(result.durationNanos),
            lineToWord = heights.lineToWord,
            wordToMedian = heights.wordToMedian,
        )
    }

    private fun foundRatio(plates: List<Rect>, textAreas: List<Rect>): Measured<Double> {
        if (textAreas.isEmpty()) {
            return Measured.Unmeasured("the annotation carries no non-empty text area")
        }
        val touched = textAreas.count { area -> RectCoverage.coveredArea(plates, listOf(area)) > 0L }
        return Measured.Value(touched.toDouble() / textAreas.size)
    }

    private fun overlapRatio(plates: List<Rect>, textAreas: List<Rect>): Measured<Double> {
        val textArea = RectCoverage.coveredArea(textAreas, null)
        if (textArea == 0L) {
            return Measured.Unmeasured("the annotated text covers no pixel")
        }
        return Measured.Value(RectCoverage.coveredArea(plates, textAreas).toDouble() / textArea)
    }

    private fun spillRatio(
        plates: List<Rect>,
        paintable: List<Rect>,
        plateArea: Long,
    ): Measured<Double> {
        val refusal = when {
            plateArea == 0L -> "the run produced no plate with area"
            paintable.isEmpty() -> "the annotation declares no area a plate may paint"
            else -> null
        }
        if (refusal != null) return Measured.Unmeasured(refusal)
        val inside = RectCoverage.coveredArea(plates, paintable)
        return Measured.Value((plateArea - inside).toDouble() / plateArea)
    }

    // The annotation is authored in whole pixels, so the comparison happens in whole pixels. Rounding
    // the plate rather than carrying its fraction keeps both sides of every ratio in one unit; the
    // sub-pixel it discards is far below any bound this corpus is meant to establish.
    private fun toRect(bounds: OverlayPlateBounds): Rect = Rect(
        bounds.left.roundToInt(),
        bounds.top.roundToInt(),
        bounds.right.roundToInt(),
        bounds.bottom.roundToInt(),
    )
}

/**
 * Exact area of a union of rectangles, optionally restricted to a second union.
 *
 * Coordinate compression rather than inclusion-exclusion: plates overlap each other routinely once
 * two lines sit close together, and adding their areas would report a spill larger than the plate.
 */
internal object RectCoverage {

    /**
     * Area covered by at least one rectangle of [primary], counting only where it is also covered by
     * at least one rectangle of [restrictTo]. A null [restrictTo] means no restriction.
     */
    fun coveredArea(primary: List<Rect>, restrictTo: List<Rect>?): Long {
        if (primary.isEmpty() || restrictTo?.isEmpty() == true) return 0L
        val all = primary + (restrictTo ?: emptyList())
        val xs = all.flatMap { listOf(it.left, it.right) }.distinct().sorted()
        val ys = all.flatMap { listOf(it.top, it.bottom) }.distinct().sorted()
        var total = 0L
        for (xi in 0 until xs.size - 1) {
            for (yi in 0 until ys.size - 1) {
                val cell = Rect(xs[xi], ys[yi], xs[xi + 1], ys[yi + 1])
                val covered = primary.any { it.containsCell(cell) } &&
                    (restrictTo == null || restrictTo.any { it.containsCell(cell) })
                if (covered) total += cell.width().toLong() * cell.height()
            }
        }
        return total
    }

    private fun Rect.containsCell(cell: Rect): Boolean =
        left <= cell.left && right >= cell.right && top <= cell.top && bottom >= cell.bottom
}
