package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * S2021: the shared catalog read, for a screen that opens, picks one channel and closes again.
 *
 * Distinct from [ObserveStreamSourcesUseCase], which hands out a cold Flow each caller collects on its
 * own: a picker dialog constructed per pick turned that into one full table read per pick. A `null`
 * value means the first read has not landed yet and is not the same as an empty catalog.
 */
class ObserveStreamCatalogSnapshotUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    operator fun invoke(): StateFlow<List<StreamSourceEntity>?> = repository.catalogSnapshot
}
