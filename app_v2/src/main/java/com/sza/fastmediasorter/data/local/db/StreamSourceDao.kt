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

    /**
     * S0756: pinned channels only, in pin order, for the main-window streams panel. A dedicated query
     * (vs filtering [observeAll]) means an unrelated catalog row write does not re-emit to the panel.
     */
    @Query("SELECT * FROM stream_sources WHERE pinned = 1 ORDER BY sortIndex ASC, addedAt DESC")
    fun observePinned(): Flow<List<StreamSourceEntity>>

    /** S0938: one-shot snapshot of the pinned set in display order, for computing a reorder move. */
    @Query("SELECT * FROM stream_sources WHERE pinned = 1 ORDER BY sortIndex ASC, addedAt DESC")
    suspend fun pinnedSnapshot(): List<StreamSourceEntity>

    /** S0938: rewrite a single pinned row's local order index during a reorder renumber. */
    @Query("UPDATE stream_sources SET sortIndex = :sortIndex WHERE id = :id")
    suspend fun setSortIndex(id: String, sortIndex: Int)

    /** Import path: ignores duplicates so a re-imported list keeps the existing local order. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(source: StreamSourceEntity): Long

    @Upsert
    suspend fun upsert(source: StreamSourceEntity)

    @Delete
    suspend fun delete(source: StreamSourceEntity)

    /** S0581: resolve the stream row for a failing playback URL, so the player can offer to remove it. */
    @Query("SELECT * FROM stream_sources WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): StreamSourceEntity?

    /**
     * S1144: persist the per-channel track preference by stable URL key. Language-codes (ADR-2), not raw
     * indices; [subtitlesEnabled] null = follow global default. The read path reuses [getByUrl].
     */
    @Query(
        "UPDATE stream_sources SET preferredAudioLang = :audioLang, " +
            "preferredSubtitleLang = :subtitleLang, subtitlesEnabled = :subtitlesEnabled WHERE url = :url"
    )
    suspend fun updateTrackPreferences(
        url: String,
        audioLang: String?,
        subtitleLang: String?,
        subtitlesEnabled: Boolean?
    )

    /** S0654: resolve just the stored media kind (RTSP/VIDEO/AUDIO) for the stream-played metric. */
    @Query("SELECT mediaKind FROM stream_sources WHERE id = :id LIMIT 1")
    suspend fun getMediaKindById(id: String): String?

    // S0404: a launcher shortcut stores the channel id, so playing or labelling it needs the row.
    @Query("SELECT * FROM stream_sources WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StreamSourceEntity?

    @Query("SELECT MIN(sortIndex) FROM stream_sources")
    suspend fun minSortIndex(): Int?

    @Query("UPDATE stream_sources SET pinned = 1, sortIndex = :newSortIndex WHERE id = :id")
    suspend fun pin(id: String, newSortIndex: Int)

    /** S0770: drop a channel's pin so it leaves the main-window streams panel; the catalog row stays. */
    @Query("UPDATE stream_sources SET pinned = 0 WHERE id = :id")
    suspend fun unpin(id: String)

    /**
     * S0660: in-place edit of a user channel. Scoped to MANUAL rows so a CATALOG/IMPORTED row can
     * never be mutated by an edit, even if the call is mis-routed; pin/sort/origin/outcome are left
     * untouched because they are absent from the SET clause.
     */
    @Query(
        "UPDATE stream_sources SET url = :url, title = :title, mediaKind = :mediaKind " +
            "WHERE id = :id AND sourceOrigin = 'MANUAL'"
    )
    suspend fun updateUserFields(id: String, url: String, title: String, mediaKind: String)

    @Query("UPDATE stream_sources SET lastPlayedAt = :atMillis WHERE id = :id")
    suspend fun markPlayed(id: String, atMillis: Long)

    /**
     * S0593: record the last local play outcome ("OK"/"FAIL") for the row status bullet.
     * S1169: conditional on a real value change - when the stored outcome already equals :outcome the
     * UPDATE matches zero rows, so SQLite's update hook does not fire and Room does not re-emit
     * observeAll(). This cuts the capture/probe -> DB write -> whole-catalog re-emit -> full rebind loop
     * for a channel whose outcome is unchanged (e.g. a chronically dead tile already UNKNOWN).
     */
    @Query(
        "UPDATE stream_sources SET lastPlayOutcome = :outcome, lastPlayOutcomeAt = :atMillis " +
            "WHERE id = :id AND (lastPlayOutcome IS NULL OR lastPlayOutcome <> :outcome)"
    )
    suspend fun markPlayOutcome(id: String, outcome: String, atMillis: Long)

    /** S0659: clear every channel's OK/FAIL status bullet. Channels themselves are untouched (no DELETE). */
    @Query("UPDATE stream_sources SET lastPlayOutcome = NULL, lastPlayOutcomeAt = NULL")
    suspend fun clearAllPlayOutcomes()

    /** S0570: snapshot of catalog-origin rows, used to compute the merge/prune delta. */
    @Query("SELECT * FROM stream_sources WHERE sourceOrigin = 'CATALOG'")
    suspend fun catalogSources(): List<StreamSourceEntity>

    /**
     * S0821: delete a bounded batch of catalog rows by url; never touches user rows. The caller
     * computes the (existing - new) prune delta in memory and feeds it here in bind-safe chunks,
     * so the import no longer depends on SQLite's bind-variable limit (the old single
     * `NOT IN (:keepUrls)` aborted large-catalog imports with "too many SQL variables").
     */
    @Query("DELETE FROM stream_sources WHERE sourceOrigin = 'CATALOG' AND url IN (:urls)")
    suspend fun deleteCatalogByUrls(urls: List<String>)

    /** S0570: refresh catalog metadata in place; sortIndex/pinned are preserved (not in the SET). */
    // A Room @Query binds one named parameter per column, so the catalog columns cannot be folded into
    // a single object here; the parameter count is inherent to the update surface.
    @Suppress("LongParameterList")
    @Query(
        "UPDATE stream_sources SET title = :title, mediaKind = :mediaKind, category = :category, " +
            "topic = :topic, language = :language, country = :country, access = :access " +
            "WHERE url = :url AND sourceOrigin = 'CATALOG'"
    )
    suspend fun updateCatalogByUrl(
        url: String,
        title: String,
        mediaKind: String,
        category: String?,
        topic: String?,
        language: String?,
        country: String?,
        // S1117: refresh the region-restriction flag on re-import so an already-imported catalog row
        // gains/loses its geo badge when the shipped catalog's verdict changes.
        access: String?
    )
}
