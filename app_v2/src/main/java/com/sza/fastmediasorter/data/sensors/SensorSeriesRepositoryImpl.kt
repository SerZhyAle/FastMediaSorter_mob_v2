package com.sza.fastmediasorter.data.sensors

import com.sza.fastmediasorter.data.local.db.SensorSeriesDao
import com.sza.fastmediasorter.data.local.db.SensorSeriesPointEntity
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesId
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesPoint
import com.sza.fastmediasorter.domain.repository.SensorSeriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1179: the chart series over Room.
 *
 * A singleton because two surfaces may read one series at once and the reset must be visible to both
 * at the same instant.
 */
@Singleton
class SensorSeriesRepositoryImpl @Inject constructor(
    private val dao: SensorSeriesDao,
) : SensorSeriesRepository {

    override fun observe(id: SensorSeriesId): Flow<List<SensorSeriesPoint>> =
        dao.observeSeries(id.storageKey).map { rows -> rows.map { it.toDomain() } }

    override suspend fun append(id: SensorSeriesId, point: SensorSeriesPoint) =
        withContext(Dispatchers.IO) { dao.insert(point.toEntity(id)) }

    override suspend fun keepOnly(id: SensorSeriesId, ids: List<Long>) =
        withContext(Dispatchers.IO) { dao.deleteOutsideKept(id.storageKey, ids) }

    override suspend fun clear(id: SensorSeriesId) =
        withContext(Dispatchers.IO) { dao.deleteSeries(id.storageKey) }
}

private fun SensorSeriesPointEntity.toDomain() = SensorSeriesPoint(
    id = id,
    takenAtMillis = takenAtMillis,
    primaryValue = primaryValue,
    secondaryValue = secondaryValue
)

private fun SensorSeriesPoint.toEntity(seriesId: SensorSeriesId) = SensorSeriesPointEntity(
    seriesId = seriesId.storageKey,
    takenAtMillis = takenAtMillis,
    primaryValue = primaryValue,
    secondaryValue = secondaryValue
)
