package com.sza.fastmediasorter.data.repository

import androidx.room.withTransaction
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.StreamSourceDao
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0565: single data entry point for the "Трансляции" stream catalog. Wraps [StreamSourceDao];
 * pin-to-top is the feature-local favorite mechanism, independent of the global favorites table.
 */
@Singleton
class StreamSourceRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: StreamSourceDao
) {

    fun observeSources(): Flow<List<StreamSourceEntity>> = dao.observeAll()

    /** S0756: pinned channels only, in pin order, for the main-window streams panel. */
    fun observePinnedSources(): Flow<List<StreamSourceEntity>> = dao.observePinned()

    suspend fun add(source: StreamSourceEntity) = dao.upsert(source)

    /** Inserts new sources, ignoring duplicates by url; returns how many were actually inserted. */
    suspend fun addAllIgnoringDuplicates(sources: List<StreamSourceEntity>): Int =
        // One transaction like the sibling mergeCatalog (S0732): a kill mid-import must not leave a
        // half-inserted playlist that observeSources re-emits as an intermediate state.
        db.withTransaction {
            var inserted = 0
            for (source in sources) {
                if (dao.insertIgnore(source) != -1L) inserted++
            }
            inserted
        }

    /** Raises a source above all others in the local list (feature-local favorite). */
    suspend fun pinToTop(id: String) {
        val newSortIndex = (dao.minSortIndex() ?: 0) - 1
        dao.pin(id, newSortIndex)
    }

    /** S0938: snapshot of the pinned set in display order, used to compute a reorder move. */
    suspend fun pinnedSnapshot(): List<StreamSourceEntity> = dao.pinnedSnapshot()

    /**
     * S0938: persist a new pinned order by renumbering the whole set contiguously (0..N-1) in one
     * transaction, so a kill mid-renumber cannot leave a partially reordered list. The caller owns the
     * move math; this only writes the id order it is given.
     */
    suspend fun reorderPinned(orderedIds: List<String>) =
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> dao.setSortIndex(id, index) }
        }

    suspend fun remove(source: StreamSourceEntity) = dao.delete(source)

    /** S0770: unpin a channel so it leaves the main-window streams panel; the catalog row is kept. */
    suspend fun unpin(id: String) = dao.unpin(id)

    /** S0660: in-place edit of a MANUAL channel's url/title/mediaKind (pin/sort/origin preserved). */
    suspend fun updateUserFields(id: String, url: String, title: String, mediaKind: String) =
        dao.updateUserFields(id, url, title, mediaKind)

    /** S0581: find the stored stream behind a playback URL (null if it is not a saved list entry). */
    suspend fun getByUrl(url: String): StreamSourceEntity? = dao.getByUrl(url)

    /** S0404: resolve a channel a launcher shortcut pinned by id (null once the user removes it). */
    suspend fun getById(id: String): StreamSourceEntity? = dao.getById(id)

    suspend fun markPlayed(id: String, atMillis: Long) = dao.markPlayed(id, atMillis)

    /** S0593: persist the last local play outcome ("OK"/"FAIL") for the streams-list status bullet. */
    suspend fun recordPlayOutcome(id: String, outcome: String) =
        dao.markPlayOutcome(id, outcome, System.currentTimeMillis())

    /** S0659: clear all OK/FAIL status bullets without removing any channel. */
    suspend fun clearPlayOutcomes() = dao.clearAllPlayOutcomes()

    /** S0654: stored media kind (RTSP/VIDEO/AUDIO) behind a source id, for the stream-played metric. */
    suspend fun getMediaKind(id: String): String? = dao.getMediaKindById(id)

    /**
     * S0570: synchronize the curated catalog into stream_sources. New catalog rows are inserted,
     * existing catalog rows have their metadata refreshed in place (sortIndex/pinned preserved), and
     * catalog rows missing from [entries] are pruned. Non-CATALOG (MANUAL/IMPORTED) rows are never
     * touched: a url already owned by a user row blocks the catalog insert and is left as-is.
     */
    suspend fun mergeCatalog(entries: List<StreamSourceEntity>): CatalogMergeResult =
        // S0732: N update/insertIgnore writes plus the final catalog prune were not atomic, so a
        // kill mid-merge left a half-synced catalog and observeSources re-emitted intermediate states.
        // One transaction makes the catalog sync all-or-nothing (single observe* emission).
        db.withTransaction {
            val existingCatalogUrls = dao.catalogSources().mapTo(HashSet()) { it.url }
            val newUrls = entries.mapTo(HashSet()) { it.url }

            var added = 0
            var updated = 0
            for (entry in entries) {
                if (entry.url in existingCatalogUrls) {
                    dao.updateCatalogByUrl(
                        url = entry.url,
                        title = entry.title,
                        mediaKind = entry.mediaKind,
                        category = entry.category,
                        topic = entry.topic,
                        language = entry.language,
                        country = entry.country,
                        access = entry.access
                    )
                    updated++
                } else if (dao.insertIgnore(entry) != -1L) {
                    added++
                }
                // insertIgnore == -1 means the url is owned by a non-CATALOG row; leave the user row alone.
            }

            // S0821: prune vanished catalog rows without binding the whole new-url set into one
            // statement. A single DELETE .. NOT IN (:keepUrls) blew past SQLite's bind-variable
            // limit and aborted the import on large catalogs (release crash, API 29). Both url sets
            // are already in memory, so delete the (existing - new) delta in bind-safe chunks - same
            // transaction, identical semantics (only CATALOG rows whose url left the new list).
            val urlsToDelete = existingCatalogUrls.filter { it !in newUrls }
            urlsToDelete.chunked(SQLITE_IN_CLAUSE_LIMIT).forEach { dao.deleteCatalogByUrls(it) }
            CatalogMergeResult(added = added, updated = updated, removed = urlsToDelete.size)
        }

    /** S0570: outcome of a [mergeCatalog] run. */
    data class CatalogMergeResult(val added: Int, val updated: Int, val removed: Int)

    private companion object {
        // SQLite caps host parameters per statement (999 on the API levels we still ship to);
        // keep delete batches under it. Mirrors FavoritesRepositoryImpl's chunking.
        const val SQLITE_IN_CLAUSE_LIMIT = 900
    }
}
