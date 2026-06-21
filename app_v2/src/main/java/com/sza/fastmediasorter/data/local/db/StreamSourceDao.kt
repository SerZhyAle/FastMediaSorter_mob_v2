package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * S0565: data access for the stream-source catalog. Ordering puts pinned sources first, then the
 * local sort index, then recency.
 */
@Dao
interface StreamSourceDao {

    @Query("SELECT * FROM stream_sources ORDER BY pinned DESC, sortIndex ASC, addedAt DESC")
    fun observeAll(): Flow<List<StreamSourceEntity>>

    /** Import path: ignores duplicates so a re-imported list keeps the existing local order. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(source: StreamSourceEntity): Long

    @Upsert
    suspend fun upsert(source: StreamSourceEntity)

    @Delete
    suspend fun delete(source: StreamSourceEntity)

    @Query("SELECT MIN(sortIndex) FROM stream_sources")
    suspend fun minSortIndex(): Int?

    @Query("UPDATE stream_sources SET pinned = 1, sortIndex = :newSortIndex WHERE id = :id")
    suspend fun pin(id: String, newSortIndex: Int)

    @Query("UPDATE stream_sources SET lastPlayedAt = :atMillis WHERE id = :id")
    suspend fun markPlayed(id: String, atMillis: Long)

    /** S0570: snapshot of catalog-origin rows, used to compute the merge/prune delta. */
    @Query("SELECT * FROM stream_sources WHERE sourceOrigin = 'CATALOG'")
    suspend fun catalogSources(): List<StreamSourceEntity>

    /** S0570: prune catalog rows that vanished from the latest catalog; never touches user rows. */
    @Query("DELETE FROM stream_sources WHERE sourceOrigin = 'CATALOG' AND url NOT IN (:keepUrls)")
    suspend fun deleteCatalogNotIn(keepUrls: List<String>)

    /** S0570: refresh catalog metadata in place; sortIndex/pinned are preserved (not in the SET). */
    @Query(
        "UPDATE stream_sources SET title = :title, mediaKind = :mediaKind, category = :category, " +
            "topic = :topic, language = :language WHERE url = :url AND sourceOrigin = 'CATALOG'"
    )
    suspend fun updateCatalogByUrl(
        url: String,
        title: String,
        mediaKind: String,
        category: String?,
        topic: String?,
        language: String?
    )
}
