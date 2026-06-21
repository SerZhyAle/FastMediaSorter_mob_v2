package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveStreamSourcesUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    operator fun invoke(): Flow<List<StreamSourceEntity>> = repository.observeSources()
}
