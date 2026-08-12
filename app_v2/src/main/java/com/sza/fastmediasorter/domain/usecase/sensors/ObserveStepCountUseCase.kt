package com.sza.fastmediasorter.domain.usecase.sensors

import com.sza.fastmediasorter.data.sensors.StepCountReadingSource
import com.sza.fastmediasorter.domain.model.sensors.StepReading
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S1179: the system step count for the steps tile.
 *
 * Passes the counter through untouched - strategic §6 item 2 and §5.1 forbid this feature from
 * deriving a step total of its own, so there is nothing for this layer to compute.
 */
class ObserveStepCountUseCase @Inject constructor(
    private val source: StepCountReadingSource,
) {

    operator fun invoke(): Flow<StepReading> = source.readings()
}
