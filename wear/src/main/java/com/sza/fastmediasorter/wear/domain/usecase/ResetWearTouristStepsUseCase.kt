package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.repository.WearTouristRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3007: resets the session step counter on Wear OS.
 */
@Singleton
class ResetWearTouristStepsUseCase @Inject constructor(
    private val touristRepository: WearTouristRepository,
) {

    operator fun invoke() {
        touristRepository.resetSteps()
    }
}

