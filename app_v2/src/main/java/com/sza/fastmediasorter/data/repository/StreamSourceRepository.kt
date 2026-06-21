package com.sza.fastmediasorter.data.repository

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
    private val dao: StreamSourceDao
) {

    fun observeSources(): Flow<List<StreamSourceEntity>> = dao.observeAll()

    suspend fun add(source: StreamSourceEntity) = dao.upsert(source)

    /** Inserts new sources, ignoring duplicates by url; returns how many were actually inserted. */
    suspend fun addAllIgnoringDuplicates(sources: List<StreamSourceEntity>): Int {
        var inserted = 0
        for (source in sources) {
            if (dao.insertIgnore(source) != -1L) inserted++
        }
        return inserted
    }

    /** Raises a source above all others in the local list (feature-local favorite). */
    suspend fun pinToTop(id: String) {
        val newSortIndex = (dao.minSortIndex() ?: 0) - 1
        dao.pin(id, newSortIndex)
    }

    suspend fun remove(source: StreamSourceEntity) = dao.delete(source)

    /** S0581: find the stored stream behind a playback URL (null if it is not a saved list entry). */
    suspend fun getByUrl(url: String): StreamSourceEntity? = dao.getByUrl(url)

    suspend fun markPlayed(id: String, atMillis: Long) = dao.markPlayed(id, atMillis)

    /** S0593: persist the last local play outcome ("OK"/"FAIL") for the streams-list status bullet. */
    suspend fun recordPlayOutcome(id: String, outcome: String) =
        dao.markPlayOutcome(id, outcome, System.currentTimeMillis())

    /**
     * S0570: synchronize the curated catalog into stream_sources. New catalog rows are inserted,
     * existing catalog rows have their metadata refreshed in place (sortIndex/pinned preserved), and
     * catalog rows missing from [entries] are pruned. Non-CATALOG (MANUAL/IMPORTED) rows are never
     * touched: a url already owned by a user row blocks the catalog insert and is left as-is.
     */
    suspend fun mergeCatalog(entries: List<StreamSourceEntity>): CatalogMergeResult {
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
                    language = entry.language
                )
                updated++
            } else if (dao.insertIgnore(entry) != -1L) {
                added++
            }
            // insertIgnore == -1 means the url is owned by a non-CATALOG row; leave the user row alone.
        }

        dao.deleteCatalogNotIn(entries.map { it.url })
        val removed = existingCatalogUrls.count { it !in newUrls }
        return CatalogMergeResult(added = added, updated = updated, removed = removed)
    }

    /** S0570: outcome of a [mergeCatalog] run. */
    data class CatalogMergeResult(val added: Int, val updated: Int, val removed: Int)
}
