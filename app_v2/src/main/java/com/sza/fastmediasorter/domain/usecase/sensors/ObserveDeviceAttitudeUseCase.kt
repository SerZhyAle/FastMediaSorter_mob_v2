package com.sza.fastmediasorter.domain.usecase.sensors

import com.sza.fastmediasorter.data.sensors.AttitudeReadingSource
import com.sza.fastmediasorter.domain.model.sensors.AttitudeReading
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** S4560: device attitude for the Tourist gyroscope tile; cold, so a collector owns the registration. */
class ObserveDeviceAttitudeUseCase @Inject constructor(
    private val source: AttitudeReadingSource,
) {

    operator fun invoke(): Flow<AttitudeReading> = source.readings()
}
