package com.sza.fastmediasorter.domain.model.sensors

/**
 * S1179: one accumulated sample of a chart series, in display units.
 *
 * [secondaryValue] is null for a series that has one dimension - a null is "this series has no
 * second value", never "the second value is zero".
 *
 * [id] is assigned by storage on append and is what decimation addresses a surviving point by; a
 * point that has not been stored yet carries 0.
 */
data class SensorSeriesPoint(
    val id: Long = 0L,
    val takenAtMillis: Long,
    val primaryValue: Double,
    val secondaryValue: Double?
)

/**
 * S1179: the chart series a point belongs to.
 *
 * [storageKey] is written into the `seriesId` column, so it is a storage format: renaming one
 * orphans every accumulated point of that series on existing installs, which is exactly the reset
 * the user did not ask for.
 */
enum class SensorSeriesId(val storageKey: String) {
    SPEED("speed"),
    ALTITUDE_DISTANCE("altitude_distance")
}
