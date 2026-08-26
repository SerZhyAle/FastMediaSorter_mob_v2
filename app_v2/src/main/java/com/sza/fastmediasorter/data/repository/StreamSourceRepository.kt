package com.sza.fastmediasorter.data.repository

import androidx.room.withTransaction
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.StreamQualityMemoryDao
import com.sza.fastmediasorter.data.local.db.StreamQualityMemoryEntity
import com.sza.fastmediasorter.data.local.db.StreamSourceDao
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.local.db.StreamUserStateDao
import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0565: single data entry point for the "Трансляции" stream catalog. Wraps [StreamSourceDao];
 * pin-to-top is the feature-local favorite mechanism, independent of the global favorites table.
 */
@Singleton
class StreamSourceRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: StreamSourceDao,
    private val streamQualityMemoryDao: StreamQualityMemoryDao,
    private val streamUserStateDao: StreamUserStateDao,
    @ApplicationScope appScope: CoroutineScope,
) {

    fun observeSources(): Flow<List<StreamSourceEntity>> = dao.observeAll()

    /**
     * S2021: one shared read of the whole catalog for the screens that pick a channel out of it.
     *
     * The launcher shortcut picker and the stream-launch widget configuration open a fresh dialog per
     * pick and each took its own first value off [observeSources], so adding five shortcuts in a row
     * paid five full table reads. [SharingStarted.WhileSubscribed] with [CATALOG_SNAPSHOT_TAIL_MS] is
     * what keeps "shared" from becoming "held for the life of the process": the read survives the gap
     * between two picks and is dropped once the user stops picking. The upstream stays collected while
     * anyone is subscribed, so Room invalidation refreshes it and no staleness window opens.
     *
     * `null` means the first read is still in flight, and is deliberately distinct from an empty list:
     * showing a loading label for a genuinely empty catalog is the defect this ticket exists for.
     */
    val catalogSnapshot: StateFlow<List<StreamSourceEntity>?> = dao.observeAll()
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(CATALOG_SNAPSHOT_TAIL_MS),
            initialValue = null,
        )

    /**
     * S1502: play outcomes as a side channel keyed by channel id. Observed separately from
     * [observeSources] so recording one channel's outcome repaints one row instead of re-emitting the
     * whole catalog - Room invalidates per table, which is why the value no longer lives on the row.
     */
    fun observePlayOutcomes(): Flow<Map<String, String>> =
        dao.observePlayOutcomesByRowId().map { outcomes ->
            outcomes.associate { it.streamId to it.outcome }
        }

    /** S0756: pinned channels only, in pin order, for the main-window streams panel. */
    fun observePinnedSources(): Flow<List<StreamSourceEntity>> = dao.observePinned()

    suspend fun add(source: StreamSourceEntity) = dao.upsert(source.withIdentity())

    /** Inserts new sources, ignoring duplicates by url; returns how many were actually inserted. */
    suspend fun addAllIgnoringDuplicates(sources: List<StreamSourceEntity>): Int =
        // One transaction like the sibling mergeCatalog (S0732): a kill mid-import must not leave a
        // half-inserted playlist that observeSources re-emits as an intermediate state.
        db.withTransaction {
            var inserted = 0
            for (source in sources) {
                if (dao.insertIgnore(source.withIdentity()) != -1L) inserted++
            }
            inserted
        }

    /**
     * Raises a source above all others in the local list (feature-local favorite).
     *
     * S1832: the durable copy is written first and the catalog row second, in one transaction - the row
     * is a projection that a merge repaints, so a pin recorded only there is a pin the next import eats.
     * The new lowest position comes from `stream_user_state`, not from the catalog: a channel currently
     * missing from the published bank still holds a position, and numbering around it would collide with
     * that channel the moment it returned.
     */
    suspend fun pinToTop(id: String) =
        db.withTransaction {
            val identity = dao.identityOf(id) ?: return@withTransaction
            val newSortIndex = (streamUserStateDao.minSortIndex() ?: 0) - 1
            streamUserStateDao.setPin(
                identityKey = identity,
                pinned = true,
                sortIndex = newSortIndex,
                atMillis = System.currentTimeMillis()
            )
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
            val now = System.currentTimeMillis()
            orderedIds.forEachIndexed { index, id ->
                dao.identityOf(id)?.let { streamUserStateDao.setSortIndex(it, index, now) }
                dao.setSortIndex(id, index)
            }
        }

    /**
     * S1502: the outcome lives outside the catalog row, so removing a channel must take it too.
     *
     * S1832: this is the user deciding a channel should go, which is the one case where the durable state
     * must NOT survive. Keeping it would resurrect the pin the user just removed the next time the bank
     * republished that address - the opposite of what an explicit delete means.
     */
    suspend fun remove(source: StreamSourceEntity) =
        db.withTransaction {
            dao.delete(source)
            streamUserStateDao.deleteByIdentity(source.identityKey)
        }

    /**
     * S0770: unpin a channel so it leaves the main-window streams panel; the catalog row is kept.
     *
     * S1832: the position is preserved rather than reset, so re-pinning the channel puts it back where it
     * was instead of at the top - the user removed it from the panel, not from their ordering.
     */
    suspend fun unpin(id: String) =
        db.withTransaction {
            val identity = dao.identityOf(id) ?: return@withTransaction
            val keptSortIndex = streamUserStateDao.stateFor(identity)?.sortIndex ?: 0
            streamUserStateDao.setPin(
                identityKey = identity,
                pinned = false,
                sortIndex = keptSortIndex,
                atMillis = System.currentTimeMillis()
            )
            dao.unpin(id)
        }

    /** S0660: in-place edit of a MANUAL channel's url/title/mediaKind (pin/sort/origin preserved). */
    suspend fun updateUserFields(id: String, url: String, title: String, mediaKind: String) =
        dao.updateUserFields(id, url, title, mediaKind, StreamChannelIdentity.of(url))

    /** S0581: find the stored stream behind a playback URL (null if it is not a saved list entry). */
    suspend fun getByUrl(url: String): StreamSourceEntity? = dao.getByUrl(url)

    /** S0404: resolve a channel a launcher shortcut pinned by id (null once the user removes it). */
    suspend fun getById(id: String): StreamSourceEntity? = dao.getById(id)

    /**
     * S1832: resolve the channel behind a launcher cell, whose payload is an identity today and was a
     * row id before this ticket.
     *
     * The two key spaces cannot collide - a row id is a UUID and an identity is a normalized address -
     * so trying one after the other is unambiguous rather than a guess. The id half stays permanently:
     * `ApplyBackupPayloadUseCase` writes cells straight out of a backup file, and a backup taken before
     * this change carries row-id payloads no migration will ever see.
     *
     * Lives here rather than in each caller so the three surfaces that resolve a cell - launch, label,
     * long-press menu - cannot drift into resolving it differently.
     */
    suspend fun getByIdentityOrId(key: String): StreamSourceEntity? {
        val byIdentity = dao.getByIdentity(key)
        return byIdentity ?: dao.getById(key)
    }

    suspend fun markPlayed(id: String, atMillis: Long) = dao.markPlayed(id, atMillis)

    /** S0593: persist the last local play outcome ("OK"/"FAIL") for the streams-list status bullet. */
    suspend fun recordPlayOutcome(id: String, outcome: String) {
        val identity = dao.identityOf(id) ?: return
        val now = System.currentTimeMillis()
        streamUserStateDao.setOutcome(identity, outcome, recordedAt = now, atMillis = now)
    }

    /** S1502: one-shot outcome read for a single channel, for surfaces that render once (info window). */
    suspend fun playOutcome(id: String): String? =
        dao.identityOf(id)?.let { streamUserStateDao.stateFor(it)?.playOutcome }

    /** S0659: clear all OK/FAIL status bullets without removing any channel - and no pin with them. */
    suspend fun clearPlayOutcomes() = streamUserStateDao.clearOutcomes()

    /**
     * S1780: drop every downloaded stream, keeping the hand-added ones.
     *
     * S1826: one transaction, because the outcome rows must go with their channels. This path deletes
     * by origin and so never learns the ids it removed, which is why it purges by what is left over
     * rather than by a list of ids the way [remove] does.
     */
    suspend fun deleteAllDownloaded(): Int =
        db.withTransaction {
            // S1832: the identities have to be read before the rows go - afterwards there is nothing left
            // to resolve them from, and the state would linger as an unreachable row that re-pins the
            // channel on the next import.
            val identities = dao.downloadedIdentities()
            val removed = dao.deleteAllDownloaded()
            identities.forEach { streamUserStateDao.deleteByIdentity(it) }
            removed
        }

    /**
     * S1511: the rung this channel last settled on, keyed by normalized address so it survives the catalog
     * re-import that reissues the row id (strategic ADR-5). The caller normalizes; this only reads.
     */
    suspend fun learnedRung(normalizedUrl: String): StreamQualityMemoryEntity? =
        streamQualityMemoryDao.learnedRungFor(normalizedUrl)

    /** S1511: persist what the session learned about one rung of one channel. */
    suspend fun rememberRung(
        normalizedUrl: String,
        bitrateBps: Int,
        widthPx: Int,
        heightPx: Int,
        failures: Int,
        atMillis: Long
    ) = streamQualityMemoryDao.rememberRung(
        url = normalizedUrl,
        bitrateBps = bitrateBps,
        widthPx = widthPx,
        heightPx = heightPx,
        failures = failures,
        atMillis = atMillis
    )

    /**
     * S1511: age the memory out and bound it, in one transaction so a kill between the two prunes cannot
     * leave the table trimmed by age but still over its channel budget.
     */
    suspend fun pruneQualityMemory(olderThanMillis: Long, keepNewestChannels: Int) =
        db.withTransaction {
            streamQualityMemoryDao.pruneOlderThan(olderThanMillis)
            streamQualityMemoryDao.pruneToNewestChannels(keepNewestChannels)
        }

    /** S0654: stored media kind (RTSP/VIDEO/AUDIO) behind a source id, for the stream-played metric. */
    suspend fun getMediaKind(id: String): String? = dao.getMediaKindById(id)

    /** S1918: number of catalog-origin rows currently stored; zero means the catalog was never imported. */
    suspend fun catalogSourceCount(): Int = dao.countCatalogSources()

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
                } else if (dao.insertIgnore(entry.withIdentity()) != -1L) {
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
            // S1832: every row that just arrived - or just came back under a new id - takes the pin and
            // the position the user gave that channel. One statement over the affected rows, inside the
            // same transaction, so the list never renders a half-restored catalog.
            dao.restorePinProjection()
            // S1832: this replaces S1826's unconditional orphan purge, which was the mechanism actively
            // deleting the history this ticket exists to keep. State for an absent channel is now kept on
            // purpose; what is bounded is the part the user never asked for - an unpinned row carrying a
            // stale OK/FAIL bullet. A pinned row is never pruned, whatever its age.
            pruneStaleUserState()
            CatalogMergeResult(added = added, updated = updated, removed = urlsToDelete.size)
        }

    /**
     * S1832: bound `stream_user_state`, which by design outlives the catalog rows pointing at it and would
     * otherwise grow without limit as the published bank churns. Callers must already be inside a
     * transaction.
     *
     * Only unpinned rows age out. A pin is the user saying "keep this", so it is kept regardless of when
     * it was last touched; what expires is an OK/FAIL bullet for a channel nobody pinned and nobody has
     * played in half a year.
     */
    private suspend fun pruneStaleUserState() {
        val cutoff = System.currentTimeMillis() - USER_STATE_RETENTION_MILLIS
        val pruned = streamUserStateDao.pruneUnpinnedOlderThan(cutoff)
        if (pruned > 0) {
            Timber.i("Stream user state: pruned %d stale unpinned rows", pruned)
        }
    }

    /**
     * S1832: every row reaches the table through this repository, so deriving the identity here is
     * what makes "no `stream_sources` row without a correct `identityKey`" an invariant rather than a
     * convention each caller has to remember. The entity keeps a Kotlin default so its many
     * construction sites stay readable; this is the only place that default is meant to be overwritten.
     */
    private fun StreamSourceEntity.withIdentity(): StreamSourceEntity =
        copy(identityKey = StreamChannelIdentity.of(url))

    /** S0570: outcome of a [mergeCatalog] run. */
    data class CatalogMergeResult(val added: Int, val updated: Int, val removed: Int)

    private companion object {
        // SQLite caps host parameters per statement (999 on the API levels we still ship to);
        // keep delete batches under it. Mirrors FavoritesRepositoryImpl's chunking.
        const val SQLITE_IN_CLAUSE_LIMIT = 900

        /** How long the shared catalog read outlives its last subscriber - enough to span two picks. */
        const val CATALOG_SNAPSHOT_TAIL_MS = 60_000L

        // S1832: how long an unpinned channel's play outcome is worth keeping once the channel itself is
        // gone from the bank. Half a year - long enough that a channel dropped for a season comes back
        // with its history, short enough that the table cannot grow forever on catalog churn.
        const val USER_STATE_RETENTION_DAYS = 180L
        const val USER_STATE_RETENTION_MILLIS = USER_STATE_RETENTION_DAYS * 24L * 60L * 60L * 1000L
    }
}
