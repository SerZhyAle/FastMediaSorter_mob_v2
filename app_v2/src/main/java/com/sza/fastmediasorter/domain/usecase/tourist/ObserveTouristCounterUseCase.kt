package com.sza.fastmediasorter.domain.usecase.tourist

import com.sza.fastmediasorter.domain.model.tourist.TouristCounterState
import com.sza.fastmediasorter.domain.repository.TouristCounterRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** S4184: the Tourist tally counter as the dashboard tile and the counter screen show it. */
class ObserveTouristCounterUseCase @Inject constructor(
    private val repository: TouristCounterRepository,
) {

    operator fun invoke(): Flow<TouristCounterState> = repository.observe()
}
