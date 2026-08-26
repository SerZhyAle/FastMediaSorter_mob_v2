package com.sza.fastmediasorter.ocrbench

/**
 * S2036: the two height relations of a scene, measured on annotated truth.
 *
 * **They are two, and the whole ticket turns on keeping them apart.** Both source documents call
 * either one "the inherited ratio", but they are derived from different quantities and land in
 * different places:
 *
 * - [SceneHeightRelation.lineToWord] - the line box height over the median word height of that line.
 *   It says how much taller a line box is than its own letters, which is the base an absolute pixel
 *   threshold has to be expressed against before it can be called relative.
 * - [SceneHeightRelation.wordToMedian] - the tallest word over the median word of its line. It is the
 *   empirical floor under the artifact-rejection multiplier: set the multiplier below the value real
 *   text reaches and real text starts being dropped.
 *
 * **The aggregation is deliberately asymmetric.** The line-to-word relation describes typical
 * material, so the median across lines is its honest summary. The word-to-median relation is a floor,
 * and a floor is set by the worst line, not by the typical one - taking its median would understate
 * exactly the case the multiplier has to survive.
 *
 * **What this cannot give.** Only a lower bound on the multiplier. Rejection fires on tokens carrying
 * no letter or digit, and annotated truth holds real text by construction, so nothing here says how
 * low the multiplier could go before it stops catching an artifact. That needs annotated artifacts,
 * which the format does not carry (strategic §6 item 4).
 */
object HeightRelation {

    /** Both relations for one scene, each able to say it was not measured and why. */
    data class SceneHeightRelation(
        val lineToWord: Measured<Double>,
        val wordToMedian: Measured<Double>,
    )

    const val NOT_SCORABLE = "the annotation is not scorable"
    const val NO_WORDS = "no text area carries word-level geometry"
    const val ZERO_MEDIAN = "a line's median word height is zero"

    fun of(annotation: SceneAnnotation): SceneHeightRelation {
        val lines = annotation.textAreas.filter { it.words.isNotEmpty() }
        val perLine = lines.map(::relationsOf)
        val refusal = when {
            !annotation.isScorable() -> NOT_SCORABLE
            lines.isEmpty() -> NO_WORDS
            perLine.any { it == null } -> ZERO_MEDIAN
            else -> null
        }
        if (refusal != null) return bothUnmeasured(refusal)

        val present = perLine.filterNotNull()
        return SceneHeightRelation(
            lineToWord = Measured.Value(median(present.map { it.lineToWord })),
            // Maximum, not median: this relation is a floor, and a floor is set by the worst line.
            wordToMedian = Measured.Value(present.maxOf { it.wordToMedian }),
        )
    }

    /** Both relations for one line. */
    private data class LineRelation(val lineToWord: Double, val wordToMedian: Double)

    /** Relations of one line, or null when its median word height is zero and nothing can be divided. */
    private fun relationsOf(area: TextArea): LineRelation? {
        val heights = area.words.map { it.box.height() }
        val medianHeight = median(heights.map { it.toDouble() })
        return if (medianHeight <= 0.0) {
            null
        } else {
            LineRelation(area.box.height() / medianHeight, heights.max() / medianHeight)
        }
    }

    private fun bothUnmeasured(reason: String): SceneHeightRelation {
        val absent = Measured.Unmeasured(reason)
        return SceneHeightRelation(absent, absent)
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
}
