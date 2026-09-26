package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.repository.WearStopwatchSessionRepository
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchOngoingIndicator
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchUpdate
import javax.inject.Inject

/**
 * S3555: every change to the measurement, and the surfaces that follow it.
 *
 * The indicator is re-posted on every change while something runs, so a lap or a second participant is
 * reflected at once, and removed on the change that stops the last runner. The programs tile is asked to
 * redraw only when running starts or stops: the tile guidance allows no per-change or per-second updates,
 * and between those two moments it has nothing new to say.
 */
class UpdateWearStopwatchUseCase @Inject constructor(
    private val session: WearStopwatchSessionRepository,
    private val indicator: WearStopwatchOngoingIndicator,
    private val requestTileRefresh: RequestWearTileRefreshUseCase
) {

    suspend operator fun invoke(
        nowMillis: Long,
        transform: (WearStopwatchState, Long) -> WearStopwatchState
    ): WearStopwatchUpdate {
        val (previous, next) = session.update(nowMillis, transform)
        if (next.anyRunning) indicator.show(next) else indicator.hide()
        if (previous.anyRunning != next.anyRunning) requestTileRefresh(WearTileKind.PROGRAMS)
        return WearStopwatchUpdate(
            state = next,
            indicatorBlocked = next.anyRunning && indicator.blockedByMissingPermission()
        )
    }
}
