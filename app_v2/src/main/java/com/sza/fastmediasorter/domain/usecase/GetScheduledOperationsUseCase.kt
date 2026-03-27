package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetScheduledOperationsUseCase @Inject constructor(
    private val repository: ScheduledOperationRepository
) {
    operator fun invoke(): Flow<List<ScheduledOperation>> = repository.getAll()
}
