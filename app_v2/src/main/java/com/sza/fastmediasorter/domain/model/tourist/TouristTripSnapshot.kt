package com.sza.fastmediasorter.domain.model.tourist

/**
 * S4145: the persisted Tourist trip - everything that must survive a process restart.
 *
 * [startedAtMillis] is null for an empty trip. Moving time, distance, gain and loss grow only while the
 * dashboard is on screen; [lastSampleAtMillis] lets the next sample tell a continuation from a gap.
 * [pressureSamples] outlive a trip reset: the trend describes the weather, not the walk.
 */
data class TouristTripSnapshot(
    val startedAtMillis: Long? = null,
    val lastSampleAtMillis: Long? = null,
    val movingMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val gainMeters: Double = 0.0,
    val lossMeters: Double = 0.0,
    val maxSpeedKmh: Float = 0f,
    val elevationSource: ElevationSource? = null,
    val lastElevationMeters: Double? = null,
    val pressureSamples: List<PressureSample> = emptyList(),
) {
    companion object {
        val EMPTY = TouristTripSnapshot()
    }
}

enum class ElevationSource {
    BAROMETER,
    GPS,
}

data class PressureSample(
    val atMillis: Long,
    val hectopascals: Float,
)
