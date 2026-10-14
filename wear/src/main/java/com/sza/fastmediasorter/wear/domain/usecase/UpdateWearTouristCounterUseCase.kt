package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.repository.WearTouristCounterRepository
import javax.inject.Inject

/**
 * S4185: every change the watch counter screen makes. The clock is read here rather than in the screen so
 * the session start and the archived end share one time source.
 */
class UpdateWearTouristCounterUseCase @Inject constructor(
    private val repository: WearTouristCounterRepository,
) {

    suspend fun add(delta: Long) {
        val now = System.currentTimeMillis()
        repository.update { it.plus(delta, now) }
    }

    suspend fun reset() {
        val now = System.currentTimeMillis()
        repository.update { it.reset(now) }
    }

    suspend fun setStartValue(start: Long) {
        repository.update { it.withStartValue(start) }
    }

    suspend fun setExtraStepsVisible(visible: Boolean) {
        repository.update { it.copy(extraStepsVisible = visible) }
    }

    suspend fun clearHistory() {
        repository.update { it.clearHistory() }
    }
}
