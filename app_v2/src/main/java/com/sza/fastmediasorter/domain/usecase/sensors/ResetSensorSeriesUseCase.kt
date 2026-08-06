package com.sza.fastmediasorter.domain.usecase.sensors

import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesId
import com.sza.fastmediasorter.domain.repository.SensorSeriesRepository
import javax.inject.Inject

/**
 * S1179: starts one chart series over, the way a trip meter is reset.
 *
 * Clearing the stored series rather than the drawing view is what makes the reset visible to every
 * surface reading it, and what zeroes the running distance total.
 */
class ResetSensorSeriesUseCase @Inject constructor(
    private val repository: SensorSeriesRepository,
) {

    suspend operator fun invoke(id: SensorSeriesId) = repository.clear(id)
}
