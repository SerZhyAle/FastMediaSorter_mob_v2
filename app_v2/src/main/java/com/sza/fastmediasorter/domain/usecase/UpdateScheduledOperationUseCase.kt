package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import javax.inject.Inject

class UpdateScheduledOperationUseCase @Inject constructor(
    private val repository: ScheduledOperationRepository
) {
    suspend operator fun invoke(operation: ScheduledOperation) =
        repository.update(operation)
}
