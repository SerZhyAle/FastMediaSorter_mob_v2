package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

/**
 * S0659: clear every stream channel's OK/FAIL play-status bullet. The channels themselves are kept -
 * only the per-row outcome is nulled - so manual and imported rows survive the reset.
 */
class ClearStreamPlayOutcomesUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
) {
    suspend operator fun invoke() = repository.clearPlayOutcomes()
}
