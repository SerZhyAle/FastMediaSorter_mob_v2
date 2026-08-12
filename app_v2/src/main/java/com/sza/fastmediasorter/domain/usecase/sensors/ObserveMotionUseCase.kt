package com.sza.fastmediasorter.domain.usecase.sensors

import com.sza.fastmediasorter.data.sensors.MotionReadingSource
import com.sza.fastmediasorter.domain.model.sensors.MotionReading
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S1179: speed, altitude and per-sample distance for the speed tile, the compass tile's altitude
 * line and both charts.
 *
 * Four consumers, one stream: strategic ADR-1 requires them to agree about the same fix rather than
 * each opening its own subscription.
 */
class ObserveMotionUseCase @Inject constructor(
    private val source: MotionReadingSource,
) {

    operator fun invoke(): Flow<MotionReading> = source.readings()
}
