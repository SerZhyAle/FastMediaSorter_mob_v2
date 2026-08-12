package com.sza.fastmediasorter.domain.usecase.sensors

import com.sza.fastmediasorter.data.sensors.OrientationReadingSource
import com.sza.fastmediasorter.domain.model.sensors.CompassReading
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S1179: heading for the compass tile.
 *
 * Deliberately thin. It exists so a gadget view depends on `domain/usecase` instead of reaching into
 * `data/sensors`, which is the chain CLAUDE.md section 8 fixes and which every other gadget already
 * follows. Added by the Phase 01 boundary audit.
 */
class ObserveCompassUseCase @Inject constructor(
    private val source: OrientationReadingSource,
) {

    operator fun invoke(): Flow<CompassReading> = source.readings()
}
