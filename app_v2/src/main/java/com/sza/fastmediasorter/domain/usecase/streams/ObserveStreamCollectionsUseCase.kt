package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamCollectionEntity
import com.sza.fastmediasorter.data.repository.StreamCollectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** S2669: the presentation layer's only door to the curated collections delivered with the catalog. */
class ObserveStreamCollectionsUseCase @Inject constructor(
    private val repository: StreamCollectionRepository
) {
    operator fun invoke(): Flow<List<StreamCollectionEntity>> = repository.observeCollections()
}
