package com.sza.fastmediasorter.wear.domain.bloodpressure

import com.sza.fastmediasorter.wear.domain.model.BloodPressureCalibration
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/** S3113: the model's own mean absolute error on the owner's pairs, each pair predicted from the others. */
data class BloodPressureError(
    val systolicMmHg: Double,
    val diastolicMmHg: Double
)

/** S3113: an estimate, or the reason there is none yet. */
sealed interface BloodPressureEstimate {

    data class NotCalibrated(val pairCount: Int, val requiredPairs: Int) : BloodPressureEstimate

    data class Estimated(
        val systolic: Int,
        val diastolic: Int,
        val pairCount: Int,
        val newestPairMillis: Long,
        val leaveOneOutError: BloodPressureError?
    ) : BloodPressureEstimate
}

/**
 * S3113: turns a feature vector into systolic and diastolic through the owner's calibration pairs.
 *
 * Ridge regression on fixed-scale, centred features, one model per output (research 04). Fixed scales
 * rather than the spread of the data, because two pairs taken minutes apart can share a feature value and
 * a data-derived scale would divide by zero exactly when the owner has just started calibrating.
 */
class EstimateBloodPressureUseCase @Inject constructor() {

    operator fun invoke(features: PulseWaveFeatures, pairs: List<BloodPressureCalibration>): BloodPressureEstimate =
        if (pairs.size < MIN_PAIRS) {
            BloodPressureEstimate.NotCalibrated(pairCount = pairs.size, requiredPairs = MIN_PAIRS)
        } else {
            BloodPressureEstimate.Estimated(
                systolic = clampToForm(predict(pairs, features) { it.systolic }),
                diastolic = clampToForm(predict(pairs, features) { it.diastolic }),
                pairCount = pairs.size,
                newestPairMillis = pairs.maxOf { it.timestampMillis },
                leaveOneOutError = if (pairs.size >= MIN_PAIRS_FOR_ERROR) leaveOneOutError(pairs) else null
            )
        }

    private fun leaveOneOutError(pairs: List<BloodPressureCalibration>): BloodPressureError {
        val errors = pairs.indices.map { held ->
            val rest = pairs.filterIndexed { index, _ -> index != held }
            val pair = pairs[held]
            abs(predict(rest, pair.features) { it.systolic } - pair.systolic) to
                abs(predict(rest, pair.features) { it.diastolic } - pair.diastolic)
        }
        return BloodPressureError(
            systolicMmHg = errors.map { it.first }.average(),
            diastolicMmHg = errors.map { it.second }.average()
        )
    }

    private fun predict(
        pairs: List<BloodPressureCalibration>,
        features: PulseWaveFeatures,
        target: (BloodPressureCalibration) -> Int
    ): Double {
        val rows = pairs.map { scaled(it.features) }
        val means = DoubleArray(FEATURE_COUNT) { column -> rows.sumOf { it[column] } / rows.size }
        val centred = rows.map { row -> DoubleArray(FEATURE_COUNT) { row[it] - means[it] } }
        val targets = pairs.map { target(it).toDouble() }
        val targetMean = targets.average()
        val gram = Array(FEATURE_COUNT) { i ->
            DoubleArray(FEATURE_COUNT) { j -> centred.sumOf { it[i] * it[j] } + if (i == j) RIDGE_LAMBDA else 0.0 }
        }
        val moments = DoubleArray(FEATURE_COUNT) { i ->
            centred.indices.sumOf { k -> centred[k][i] * (targets[k] - targetMean) }
        }
        val slopes = solve(gram, moments)
        val query = scaled(features)
        return targetMean + (0 until FEATURE_COUNT).sumOf { slopes[it] * (query[it] - means[it]) }
    }

    private fun scaled(features: PulseWaveFeatures): DoubleArray = doubleArrayOf(
        features.heartRateBpm / HEART_RATE_SCALE_BPM,
        features.systolicUpstrokeSeconds / UPSTROKE_SCALE_SECONDS,
        features.pulseWidth50Seconds / WIDTH_SCALE_SECONDS
    )

    /** Gaussian elimination with partial pivoting; the ridge term keeps the system positive definite. */
    private fun solve(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        val a = Array(matrix.size) { matrix[it].copyOf() }
        val b = vector.copyOf()
        for (pivot in a.indices) {
            val best = (pivot until a.size).maxBy { abs(a[it][pivot]) }
            a[pivot] = a[best].also { a[best] = a[pivot] }
            b[pivot] = b[best].also { b[best] = b[pivot] }
            for (row in pivot + 1 until a.size) {
                val factor = a[row][pivot] / a[pivot][pivot]
                for (column in pivot until a.size) {
                    a[row][column] -= factor * a[pivot][column]
                }
                b[row] -= factor * b[pivot]
            }
        }
        val solution = DoubleArray(b.size)
        for (row in a.indices.reversed()) {
            val known = (row + 1 until a.size).sumOf { a[row][it] * solution[it] }
            solution[row] = (b[row] - known) / a[row][row]
        }
        return solution
    }

    private fun clampToForm(value: Double): Int = value.roundToInt().coerceIn(MIN_MMHG, MAX_MMHG)

    companion object {

        /** One pair gives no slope without a population model, which the strategic spec excludes. */
        const val MIN_PAIRS = 2
        private const val MIN_PAIRS_FOR_ERROR = 3
        private const val FEATURE_COUNT = 3
        private const val RIDGE_LAMBDA = 1.0
        private const val HEART_RATE_SCALE_BPM = 10.0
        private const val UPSTROKE_SCALE_SECONDS = 0.02
        private const val WIDTH_SCALE_SECONDS = 0.05
        private const val MIN_MMHG = 40
        private const val MAX_MMHG = 300
    }
}
