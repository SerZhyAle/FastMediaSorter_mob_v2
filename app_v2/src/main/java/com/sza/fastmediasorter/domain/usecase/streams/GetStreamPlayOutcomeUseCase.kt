package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

/**
 * S1502: the last recorded play outcome of one channel, or null when it has never been tried.
 *
 * A one-shot read rather than a slice of [ObserveStreamPlayOutcomesUseCase]: the caller is the "about
 * this channel" window, which renders its stored half once and observes nothing.
 */
class GetStreamPlayOutcomeUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(id: String): String? = repository.playOutcome(id)
}
