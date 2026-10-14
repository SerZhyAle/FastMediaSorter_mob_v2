package com.sza.fastmediasorter.wear.domain.tourist

/**
 * S4146: the persisted watch Tourist trip - everything that must survive a process restart.
 *
 * [startedAtMillis] is null for an empty trip. Every instant is wall-clock, so a reboot needs no
 * rebase. [lastSampleAtMillis] lets the next sample tell a continuation from a gap.
 * [pressureSamples] outlive a trip reset: the trend describes the weather, not the walk.
 */
data class WearTripSnapshot(
    val startedAtMillis: Long? = null,
    val lastSampleAtMillis: Long? = null,
    val movingMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val gainMeters: Double = 0.0,
    val lossMeters: Double = 0.0,
    val maxSpeedKmh: Float = 0f,
    val elevationSource: WearElevationSource? = null,
    val lastElevationMeters: Double? = null,
    val pressureSamples: List<WearPressureSample> = emptyList(),
) {
    companion object {
        val EMPTY = WearTripSnapshot()
    }
}

enum class WearElevationSource {
    BAROMETER,
    GPS,
}

data class WearPressureSample(
    val atMillis: Long,
    val hectopascals: Float,
)
