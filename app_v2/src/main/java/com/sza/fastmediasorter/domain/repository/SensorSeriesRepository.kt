package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesId
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesPoint
import kotlinx.coroutines.flow.Flow

/**
 * S1179: the accumulated chart series, owned by the series rather than by whatever is drawing it.
 *
 * A chart leaving the desktop does not end its series - strategic ADR-2 - so clearing is an explicit
 * operation here and never a side effect of a view being detached.
 */
interface SensorSeriesRepository {

    fun observe(id: SensorSeriesId): Flow<List<SensorSeriesPoint>>

    suspend fun append(id: SensorSeriesId, point: SensorSeriesPoint)

    /**
     * Thins the series down to [ids]. The caller decides which points survive, because the rule that
     * picks them is a display concern the storage layer must not own.
     */
    suspend fun keepOnly(id: SensorSeriesId, ids: List<Long>)

    suspend fun clear(id: SensorSeriesId)
}
