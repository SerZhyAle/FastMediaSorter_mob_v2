package com.sza.fastmediasorter.domain.usecase.sensors

import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesId
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesPoint
import com.sza.fastmediasorter.domain.repository.SensorSeriesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S1179: the accumulated points of one chart series, oldest first.
 */
class ObserveSensorSeriesUseCase @Inject constructor(
    private val repository: SensorSeriesRepository,
) {

    operator fun invoke(id: SensorSeriesId): Flow<List<SensorSeriesPoint>> = repository.observe(id)
}
