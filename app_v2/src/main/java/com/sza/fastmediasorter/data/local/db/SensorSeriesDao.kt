package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorSeriesDao {

    @Query("SELECT * FROM sensor_series_point WHERE seriesId = :seriesId ORDER BY takenAtMillis ASC")
    fun observeSeries(seriesId: String): Flow<List<SensorSeriesPointEntity>>

    @Insert
    suspend fun insert(point: SensorSeriesPointEntity)

    @Query("DELETE FROM sensor_series_point WHERE seriesId = :seriesId")
    suspend fun deleteSeries(seriesId: String)

    /**
     * Thins a series down to [keptIds] in one statement. Deleting by a keep-list rather than by an
     * age or a row limit is what keeps the oldest sample alive: the series means "since the last
     * reset", and an eviction keyed on age would silently turn it into a moving window.
     *
     * [keptIds] costs one SQLite variable each, against a 999-variable ceiling. The caller halves a
     * series capped at 720 rows, so it binds about 361; a cap raised past roughly 1996 would fail
     * here at run time rather than at compile time.
     */
    @Query("DELETE FROM sensor_series_point WHERE seriesId = :seriesId AND id NOT IN (:keptIds)")
    suspend fun deleteOutsideKept(seriesId: String, keptIds: List<Long>)
}
