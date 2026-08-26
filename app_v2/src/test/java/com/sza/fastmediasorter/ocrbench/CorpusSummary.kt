package com.sza.fastmediasorter.ocrbench

/**
 * S1716: what the whole corpus says, per axis, with the scenes it could not say it about counted
 * apart.
 *
 * Worst and median are taken over measured scenes only. Averaging an unmeasured scene in as a zero
 * is the exact failure this ticket was opened for, and dropping it silently is the same failure with
 * the evidence removed - so it leaves the statistics and enters [AxisSummary.unmeasuredCount], which
 * strategic §11 criterion 2 requires the report to print as its own field.
 */
data class CorpusSummary(
    val sceneCount: Int,
    val axes: List<AxisSummary>,
) {

    fun axis(name: String): AxisSummary =
        axes.first { it.axis == name }

    companion object {

        const val AXIS_FOUND = "found"
        const val AXIS_OVERLAP = "overlap"
        const val AXIS_SPILL = "spill"
        const val AXIS_DURATION = "durationNanos"

        /**
         * S2036: kept as two axes, never combined. They are derived from different quantities and read
         * for different decisions, and a single row would put the conflation the ticket exists to
         * prevent in the one place a reader actually looks.
         */
        const val AXIS_LINE_TO_WORD = "lineToWord"
        const val AXIS_WORD_TO_MEDIAN = "wordToMedian"

        fun of(metrics: List<SceneMetrics>): CorpusSummary = CorpusSummary(
            sceneCount = metrics.size,
            axes = listOf(
                AxisSummary.of(AXIS_FOUND, metrics.map { it.found }, Worse.LOWER),
                AxisSummary.of(AXIS_OVERLAP, metrics.map { it.overlap }, Worse.LOWER),
                AxisSummary.of(AXIS_SPILL, metrics.map { it.spill }, Worse.HIGHER),
                AxisSummary.of(
                    AXIS_DURATION,
                    metrics.map { measured -> measured.durationNanos.asDouble() },
                    Worse.HIGHER,
                ),
                // Higher is worse on both: a line box far taller than its letters and a word far taller
                // than its line's median are each the direction that stresses an inherited constant.
                AxisSummary.of(AXIS_LINE_TO_WORD, metrics.map { it.lineToWord }, Worse.HIGHER),
                AxisSummary.of(AXIS_WORD_TO_MEDIAN, metrics.map { it.wordToMedian }, Worse.HIGHER),
            ),
        )

        private fun Measured<Long>.asDouble(): Measured<Double> = when (this) {
            is Measured.Value -> Measured.Value(value.toDouble())
            is Measured.Unmeasured -> this
        }
    }
}

/** Which end of an axis is the bad end. Without it "worst" would silently mean "largest". */
enum class Worse { LOWER, HIGHER }

/** One axis across the corpus. Null worst and median mean nothing on this axis was measured. */
data class AxisSummary(
    val axis: String,
    val worst: Double?,
    val median: Double?,
    val measuredCount: Int,
    val unmeasuredCount: Int,
    val unmeasuredReasons: List<String>,
) {

    companion object {

        fun of(axis: String, values: List<Measured<Double>>, worse: Worse): AxisSummary {
            val measured = values.filterIsInstance<Measured.Value<Double>>().map { it.value }
            val reasons = values.filterIsInstance<Measured.Unmeasured>().map { it.reason }
            return AxisSummary(
                axis = axis,
                worst = worstOf(measured, worse),
                median = medianOf(measured),
                measuredCount = measured.size,
                unmeasuredCount = reasons.size,
                unmeasuredReasons = reasons.distinct(),
            )
        }

        private fun worstOf(values: List<Double>, worse: Worse): Double? = when (worse) {
            Worse.LOWER -> values.minOrNull()
            Worse.HIGHER -> values.maxOrNull()
        }

        private fun medianOf(values: List<Double>): Double? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) {
                sorted[middle]
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2
            }
        }
    }
}
