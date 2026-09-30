package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.repository.WearTouristRepository
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3007: use case observing live telemetry. The focused metric is screen state held by the view model,
 * not an input here.
 */
@Singleton
class ObserveWearTouristDashboardUseCase @Inject constructor(
    private val touristRepository: WearTouristRepository,
) {

    operator fun invoke(): Flow<WearTouristState> = touristRepository.observeTelemetry()
}
