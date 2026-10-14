package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.repository.WearTouristCounterRepository
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristCounterState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** S4185: the watch Tourist counter as the tile and the counter screen read it. */
class ObserveWearTouristCounterUseCase @Inject constructor(
    private val repository: WearTouristCounterRepository,
) {

    operator fun invoke(): Flow<WearTouristCounterState> = repository.observe()
}
