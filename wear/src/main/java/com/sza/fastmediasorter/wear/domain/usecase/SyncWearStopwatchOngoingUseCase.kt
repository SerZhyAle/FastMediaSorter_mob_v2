package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.repository.WearStopwatchSessionRepository
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchOngoingIndicator
import javax.inject.Inject

/**
 * S3555: puts the indicator back in line with the stored measurement.
 *
 * Needed where no change happens but the indicator may be wrong: a process restored after the system
 * unloaded it, or a notification permission granted after the measurement had already started.
 */
class SyncWearStopwatchOngoingUseCase @Inject constructor(
    private val session: WearStopwatchSessionRepository,
    private val indicator: WearStopwatchOngoingIndicator
) {

    suspend operator fun invoke() {
        val current = session.current()
        if (current.anyRunning) indicator.show(current) else indicator.hide()
    }
}
