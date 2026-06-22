package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

class RemoveStreamSourceUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(source: StreamSourceEntity) = repository.remove(source)
}
