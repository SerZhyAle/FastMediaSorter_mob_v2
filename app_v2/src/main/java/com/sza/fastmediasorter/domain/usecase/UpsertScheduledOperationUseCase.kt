package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import javax.inject.Inject

class UpsertScheduledOperationUseCase @Inject constructor(
    private val repository: ScheduledOperationRepository
) {
    suspend operator fun invoke(operation: ScheduledOperation): Long =
        repository.upsert(operation)
}
