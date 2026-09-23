package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.heading.CompassHeadingDataSource
import com.sza.fastmediasorter.wear.domain.model.HeadingReading
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S3370: heading for the dim overlay's spark pair.
 *
 * Deliberately thin, same shape as the phone's `ObserveCompassUseCase`: a consumer depends on
 * `domain/usecase` instead of reaching into `data/heading`.
 */
class ObserveHeadingUseCase @Inject constructor(
    private val source: CompassHeadingDataSource,
) {

    operator fun invoke(): Flow<HeadingReading> = source.readings()
}
