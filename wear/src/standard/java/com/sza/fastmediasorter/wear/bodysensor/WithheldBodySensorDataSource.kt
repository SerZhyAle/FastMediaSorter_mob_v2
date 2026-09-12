package com.sza.fastmediasorter.wear.bodysensor

import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorReading
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.bodysensor.WearBodySensorDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * The store build's answer: there is no heart-rate path here, and there was never meant to be one.
 *
 * Play reviews both `BODY_SENSORS` and `health.READ_HEART_RATE` against six admitted use cases - fitness
 * and coaching, rewards, corporate wellness, medical care, human-subjects research, activity games - and a
 * media sorter matches none. S2457 ADR-1 therefore keeps the permission, the Health Services dependency
 * and the measurement out of this flavor entirely, so that a review of a shipping listing is never opened
 * by a diagnostic screen.
 *
 * This class exists because the contract has two sides and exactly one flavor set is ever on the
 * classpath (`dev/FLAVOR_DEVELOPMENT_RULES.md` Rule 8): without it the `standard` build fails at Hilt
 * resolution, since the ViewModel that injects the contract compiles in both flavors.
 */
class WithheldBodySensorDataSource @Inject constructor() : WearBodySensorDataSource {

    override suspend fun availability(): BodySensorReading = WITHHELD

    override fun measure(): Flow<BodySensorReading> = flowOf(WITHHELD)

    private companion object {
        val WITHHELD = BodySensorReading.Unavailable(
            BodySensorUnavailableReason.NOT_OFFERED_IN_THIS_BUILD
        )
    }
}
