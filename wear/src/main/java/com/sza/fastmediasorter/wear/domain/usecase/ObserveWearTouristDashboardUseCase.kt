package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.repository.WearTouristRepository
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3007: use case observing live telemetry combined with user preference for the focused metric.
 */
@Singleton
class ObserveWearTouristDashboardUseCase @Inject constructor(
    private val touristRepository: WearTouristRepository,
) {

    operator fun invoke(initialFocus: TouristMetricType = TouristMetricType.SPEED): Flow<WearTouristState> {
        return touristRepository.observeTelemetry()
    }
}
