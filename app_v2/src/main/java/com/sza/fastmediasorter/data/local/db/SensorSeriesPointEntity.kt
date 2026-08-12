package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * S1179: one sample of one chart series, accumulated since that series was last reset.
 *
 * Two value columns rather than one table per chart: the speed series fills [primaryValue] alone,
 * the altitude series fills [primaryValue] with altitude and [secondaryValue] with the running
 * distance, so a future third chart needs no schema change. [secondaryValue] is null - not zero -
 * for a series that has no second dimension, because a chart cannot otherwise tell "no reading" from
 * "reading of zero".
 */
@Entity(tableName = "sensor_series_point", indices = [Index(value = ["seriesId", "takenAtMillis"])])
data class SensorSeriesPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seriesId: String,
    val takenAtMillis: Long,
    val primaryValue: Double,
    val secondaryValue: Double?
)
