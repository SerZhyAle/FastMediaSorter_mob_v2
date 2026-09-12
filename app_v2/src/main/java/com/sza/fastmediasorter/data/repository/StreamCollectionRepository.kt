package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.StreamCollectionDao
import com.sza.fastmediasorter.data.local.db.StreamCollectionEntity
import com.sza.fastmediasorter.data.local.db.StreamCollectionMemberEntity
import com.sza.fastmediasorter.data.repository.StreamCollectionsJsonParser.ParsedStreamCollections
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2669: the single door to the curated-collection tables.
 *
 * Stores the locale map verbatim and resolves no locale: which name a user sees depends on the app
 * locale, which is known at the presentation edge and not here. That is what keeps a newly delivered
 * locale reaching the user without an app release.
 */
@Singleton
class StreamCollectionRepository @Inject constructor(
    private val dao: StreamCollectionDao
) {

    fun observeCollections(): Flow<List<StreamCollectionEntity>> = dao.observeCollections()

    fun observeMembers(collectionId: String): Flow<List<StreamCollectionMemberEntity>> =
        dao.observeMembers(collectionId)

    suspend fun membersOf(collectionId: String): List<StreamCollectionMemberEntity> =
        dao.membersOf(collectionId)

    suspend fun hasAnyCollection(): Boolean = dao.countCollections() > 0

    /**
     * Replaces the stored set whole. Called only for an archive that actually carried the entry - an
     * archive without it must leave what is stored alone, which is why the emptiness check belongs to
     * the caller and not here.
     */
    suspend fun replaceAll(parsed: ParsedStreamCollections) {
        val collections = parsed.collections.map { collection ->
            StreamCollectionEntity(
                collectionId = collection.id,
                sortOrder = collection.sortOrder,
                namesJson = collection.namesJson
            )
        }
        val members = parsed.collections.flatMap { collection ->
            collection.memberUrls.mapIndexed { index, url ->
                StreamCollectionMemberEntity(
                    collectionId = collection.id,
                    url = url,
                    sortOrder = index + 1
                )
            }
        }
        dao.replaceAll(collections, members)
    }
}
