package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamCollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S2669: the presentation layer's only door to the curated collections delivered with the catalog.
 *
 * The UI sees [StreamCollection], never the Room entity: the layer rule (S2103) keeps the
 * persistence schema out of screens, and the entity carries nothing the screen needs beyond these
 * three fields - `namesJson` stays raw because the locale is resolved at the presentation edge.
 */
class ObserveStreamCollectionsUseCase @Inject constructor(
    private val repository: StreamCollectionRepository
) {

    operator fun invoke(): Flow<List<StreamCollection>> = repository.observeCollections().map { list ->
        list.map { entity ->
            StreamCollection(
                id = entity.collectionId,
                sortOrder = entity.sortOrder,
                namesJson = entity.namesJson
            )
        }
    }

    /**
     * Stream url -> the curator's position inside one collection. Read once per selection change,
     * not per row: the filter pass walks the whole bank on every keystroke, so the membership is
     * handed over as a ready map (strategic 3.2, performance).
     */
    suspend fun memberOrder(collectionId: String): Map<String, Int> =
        repository.membersOf(collectionId).associate { it.url to it.sortOrder }

    /** One delivered collection, stripped of everything storage-specific. */
    data class StreamCollection(
        val id: String,
        val sortOrder: Int,
        val namesJson: String
    )
}
