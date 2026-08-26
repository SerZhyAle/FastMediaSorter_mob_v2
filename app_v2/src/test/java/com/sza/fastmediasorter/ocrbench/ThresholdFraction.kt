package com.sza.fastmediasorter.ocrbench

import com.sza.fastmediasorter.domain.ocr.OcrBlockFilter

/**
 * S2036: the live absolute box thresholds, expressed as a fraction of the scene's median line height.
 *
 * This is the number ADR-3 is decided from. The thresholds are absolute pixel bounds on the source
 * image, so the same screenshot taken at a higher density is filtered differently - a fraction of the
 * line height is the form that scales with the material instead. Whether it is worth adopting is not
 * an argument, it is the spread of [heightFraction] across scenes of different resolutions: a stable
 * fraction means relativity buys nothing, a spread means the absolute form is the defect.
 *
 * **Both thresholds are read from [OcrBlockFilter], never copied.** A restated value would keep
 * printing the old fraction after someone changed the filter, and a stale fraction is indistinguishable
 * from a measured one.
 */
object ThresholdFraction {

    const val NO_TEXT_AREA = "the annotation carries no non-empty text area"

    /** One scene's resolution, its median annotated line height, and what the thresholds are worth there. */
    data class SceneFraction(
        val sceneId: String,
        val widthPx: Int,
        val heightPx: Int,
        val medianLineHeight: Measured<Double>,
        /** [OcrBlockFilter.MIN_BOX_WIDTH] over the median line height. */
        val widthFraction: Measured<Double>,
        /** [OcrBlockFilter.MIN_BOX_HEIGHT] over the median line height. */
        val heightFraction: Measured<Double>,
    )

    fun of(annotation: SceneAnnotation): SceneFraction {
        val heights = annotation.textAreas.map { it.box.height() }.filter { it > 0 }
        if (heights.isEmpty()) {
            val absent = Measured.Unmeasured(NO_TEXT_AREA)
            return SceneFraction(annotation.sceneId, annotation.widthPx, annotation.heightPx, absent, absent, absent)
        }
        val median = median(heights.map { it.toDouble() })
        return SceneFraction(
            sceneId = annotation.sceneId,
            widthPx = annotation.widthPx,
            heightPx = annotation.heightPx,
            medianLineHeight = Measured.Value(median),
            widthFraction = Measured.Value(OcrBlockFilter.MIN_BOX_WIDTH / median),
            heightFraction = Measured.Value(OcrBlockFilter.MIN_BOX_HEIGHT / median),
        )
    }

    /**
     * Difference between the largest and smallest measured height fraction, or null when the corpus
     * cannot produce a meaningful one.
     *
     * **The gate is two distinct resolutions, not two scenes.** An absolute pixel threshold is only
     * wrong because it fails to scale, so the spread answers ADR-3 only when something about the
     * material actually changed scale. Six scenes at one resolution produce a spread of zero by
     * construction, and a printed 0 there reads as "the fraction is stable, so relativity buys
     * nothing" - the exact conclusion this number exists to support or refuse, reached without
     * evidence. Null says the corpus did not ask the question; it never says the answer was no.
     */
    fun heightFractionSpread(fractions: List<SceneFraction>): Double? {
        val measured = fractions.filter { it.heightFraction is Measured.Value }
        val resolutions = measured.map { it.widthPx to it.heightPx }.distinct()
        if (resolutions.size < MIN_RESOLUTIONS_FOR_SPREAD) return null

        val values = measured.mapNotNull { (it.heightFraction as? Measured.Value)?.value }
        return values.max() - values.min()
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
    }

    private const val MIN_RESOLUTIONS_FOR_SPREAD = 2
}
