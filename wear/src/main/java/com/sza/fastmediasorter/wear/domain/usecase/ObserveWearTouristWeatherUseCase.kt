package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearTouristWeatherPayload
import com.sza.fastmediasorter.wear.domain.repository.WearTouristWeatherRepository
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristWeatherRequestPolicy
import com.sza.fastmediasorter.wear.domain.tourist.WearWeatherPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S4152: the Tourist weather while the screen collects it - asking the source per
 * [WearTouristWeatherRequestPolicy] and passing on only an answer young enough to show.
 *
 * Both halves run on a clock as well as on their input: a watch standing still emits no new position, yet
 * the interval must still ask again, and an answer that stops being refreshed must still disappear.
 */
class ObserveWearTouristWeatherUseCase @Inject constructor(
    private val repository: WearTouristWeatherRepository,
) {

    operator fun invoke(positions: Flow<WearWeatherPosition?>): Flow<WearTouristWeatherPayload?> = channelFlow {
        launch { requestAsNeeded(positions) }
        combine(repository.observeWeather(), clockTicks()) { answer, now ->
            answer?.takeIf { WearTouristWeatherRequestPolicy.isFresh(it.observedAtMs, now) }
        }
            .distinctUntilChanged()
            .collect { answer ->
                Timber.d("S4152: weather tiles fresh=%s", answer != null)
                send(answer)
            }
    }

    private suspend fun requestAsNeeded(positions: Flow<WearWeatherPosition?>) {
        var lastPosition: WearWeatherPosition? = null
        var lastRequestAtMs: Long? = null
        combine(positions.filterNotNull(), clockTicks()) { raw, now ->
            WearTouristWeatherRequestPolicy.round(raw.latitude, raw.longitude) to now
        }.collect { (position, now) ->
            if (WearTouristWeatherRequestPolicy.shouldRequest(lastPosition, lastRequestAtMs, position, now)) {
                lastPosition = position
                lastRequestAtMs = now
                repository.requestWeather(position)
            }
        }
    }

    private fun clockTicks(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_TICK_MS)
        }
    }

    private companion object {
        const val CLOCK_TICK_MS = 60_000L
    }
}
