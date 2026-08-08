package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S1502: per-channel play outcomes, keyed by channel id, as a side channel beside the catalog list.
 */
class ObserveStreamPlayOutcomesUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    operator fun invoke(): Flow<Map<String, String>> = repository.observePlayOutcomes()
}
