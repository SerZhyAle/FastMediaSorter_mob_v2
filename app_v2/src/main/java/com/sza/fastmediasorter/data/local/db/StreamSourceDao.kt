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

    /**
     * S1832: the row a launcher cell's stored identity points at.
     *
     * The identity index is not unique - the published bank folds 58 groups of addresses onto one key -
     * so this picks the most recently added of them. Any of them answers the question equally well,
     * because sharing an identity is what makes them the same channel; the ordering exists only so two
     * calls a second apart cannot disagree.
     */
    @Query("SELECT * FROM stream_sources WHERE identityKey = :identityKey ORDER BY addedAt DESC LIMIT 1")
    suspend fun getByIdentity(identityKey: String): StreamSourceEntity?

    /**
     * S1832: repaint the pin projection on the catalog rows from the durable user state, in one
     * statement. Called at the end of a merge, so a channel that just returned to the bank as a brand new
     * row picks the pin and the position back up.
     *
     * Restricted to rows that either hold user state or currently claim a pin: an unrestricted UPDATE
     * would rewrite all 17 628 rows of the published bank and invalidate the whole table for the sake of
     * a few dozen pinned ones.
     */
    @Query(
        "UPDATE stream_sources SET " +
            "pinned = COALESCE((SELECT us.pinned FROM stream_user_state us " +
            "WHERE us.identityKey = stream_sources.identityKey), 0), " +
            "sortIndex = COALESCE((SELECT us.sortIndex FROM stream_user_state us " +
            "WHERE us.identityKey = stream_sources.identityKey), sortIndex) " +
            "WHERE identityKey IN (SELECT identityKey FROM stream_user_state) OR pinned = 1"
    )
    suspend fun restorePinProjection()

    /**
     * S1832: the outcome map the streams list renders, still keyed by row id so no caller above the
     * repository changes, but now sourced from the identity-keyed durable state.
     *
     * The join makes this Flow observe `stream_sources` as well as `stream_user_state`, so a catalog
     * merge re-emits it. That costs one extra emission at the exact moment the list re-renders anyway,
     * which is why it is preferred over changing the contract every consumer is written against.
     */
    @Query(
        "SELECT s.id AS streamId, us.playOutcome AS outcome FROM stream_sources s " +
            "INNER JOIN stream_user_state us ON us.identityKey = s.identityKey " +
            "WHERE us.playOutcome IS NOT NULL"
    )
    fun observePlayOutcomesByRowId(): Flow<List<StreamRowOutcome>>

    /** S1832: the key a single-channel writer files its state under, resolved from the row it was given. */
    @Query("SELECT identityKey FROM stream_sources WHERE id = :id LIMIT 1")
    suspend fun identityOf(id: String): String?

    /** S1832: identities of the rows a bulk delete is about to remove, so their user state can go too. */
    @Query("SELECT identityKey FROM stream_sources WHERE sourceOrigin IN ('CATALOG', 'IMPORTED')")
    suspend fun downloadedIdentities(): List<String>

    @Query("SELECT MIN(sortIndex) FROM stream_sources")
    suspend fun minSortIndex(): Int?

    @Query("UPDATE stream_sources SET pinned = 1, sortIndex = :newSortIndex WHERE id = :id")
    suspend fun pin(id: String, newSortIndex: Int)

    /** S0770: drop a channel's pin so it leaves the main-window streams panel; the catalog row stays. */
    @Query("UPDATE stream_sources SET pinned = 0 WHERE id = :id")
    suspend fun unpin(id: String)

    /**
     * S0660: in-place edit of a user channel. Scoped to MANUAL rows so a CATALOG/IMPORTED row can
     * never be mutated by an edit, even if the call is mis-routed; pin/sort/origin are left
     * untouched because they are absent from the SET clause.
     *
     * S1832: [identityKey] moves with [url]. Editing a manual channel's address makes it a different
     * channel as far as user-authored state is concerned, so leaving the old key behind would file
     * the new address's pin and history under the address it no longer has.
     */
    @Query(
        "UPDATE stream_sources SET url = :url, title = :title, mediaKind = :mediaKind, " +
            "identityKey = :identityKey WHERE id = :id AND sourceOrigin = 'MANUAL'"
    )
    suspend fun updateUserFields(
        id: String,
        url: String,
        title: String,
        mediaKind: String,
        identityKey: String
    )

    @Query("UPDATE stream_sources SET lastPlayedAt = :atMillis WHERE id = :id")
    suspend fun markPlayed(id: String, atMillis: Long)

    /** S0570: snapshot of catalog-origin rows, used to compute the merge/prune delta. */
    @Query("SELECT * FROM stream_sources WHERE sourceOrigin = 'CATALOG'")
    suspend fun catalogSources(): List<StreamSourceEntity>

    /**
     * S1918: how many catalog rows are stored, answered on the database side. Callers that only need
     * "was the catalog ever downloaded" must not pull [catalogSources] - after a successful import it
     * holds thousands of rows.
     */
    @Query("SELECT COUNT(*) FROM stream_sources WHERE sourceOrigin = 'CATALOG'")
    suspend fun countCatalogSources(): Int

    /**
     * S1780: everything that arrived by download, and nothing the user typed.
     *
     * CATALOG rows come from the curated catalog and IMPORTED rows from a playlist this app fetched over
     * HTTP, so both are "downloaded from the internet" in the owner's sense; MANUAL rows were entered by
     * hand and survive. Returns how many rows went, so the caller can say so instead of guessing.
     */
    @Query("DELETE FROM stream_sources WHERE sourceOrigin IN ('CATALOG', 'IMPORTED')")
    suspend fun deleteAllDownloaded(): Int

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
