package com.sza.fastmediasorter.wear.bodysensor

import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgCapture
import com.sza.fastmediasorter.wear.domain.bodysensor.WearPpgDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * S3113: the store build's answer - no pulse-wave path, for the reason [WithheldBodySensorDataSource]
 * records. The blood-pressure screen is not offered in this flavor at all; the class exists because the
 * view model that injects the contract compiles in both flavors and Hilt must resolve it here too.
 */
class WithheldPpgDataSource @Inject constructor() : WearPpgDataSource {

    override suspend fun unavailableReason(): BodySensorUnavailableReason = WITHHELD

    override fun capture(durationMillis: Long): Flow<PpgCapture> = flowOf(PpgCapture.Unavailable(WITHHELD))

    private companion object {
        val WITHHELD = BodySensorUnavailableReason.NOT_OFFERED_IN_THIS_BUILD
    }
}
