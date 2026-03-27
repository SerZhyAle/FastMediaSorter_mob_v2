package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import javax.inject.Inject

class ClearScheduledOperationsUseCase @Inject constructor(
    private val repository: ScheduledOperationRepository
) {
    suspend operator fun invoke() = repository.deleteAll()
}
