package com.sza.fastmediasorter.wear.data.repository

import com.sza.fastmediasorter.wear.data.db.HeartRateHistoryDao
import com.sza.fastmediasorter.wear.data.db.HeartRateHistoryEntity
import com.sza.fastmediasorter.wear.data.db.toDomain
import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry
import com.sza.fastmediasorter.wear.domain.repository.HeartRateHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S2808: Room-backed implementation of [HeartRateHistoryRepository].
 *
 * The timestamp is stamped at save time so the ViewModel does not need to pass it,
 * and the entity-to-domain mapping keeps the Room type out of the UI layer.
 */
class HeartRateHistoryRepositoryImpl @Inject constructor(
    private val dao: HeartRateHistoryDao
) : HeartRateHistoryRepository {

    override suspend fun save(bpm: Int) {
        dao.insert(
            HeartRateHistoryEntity(
                bpm = bpm,
                timestampMillis = System.currentTimeMillis()
            )
        )
        // S3112: the trim rides on the insert because this is the only write path into the table, so
        // the row count cannot grow at any other moment and no periodic job is needed to notice.
        dao.trimToNewest(MAX_HISTORY_ENTRIES)
    }

    override fun observeAll(): Flow<List<HeartRateHistoryEntry>> =
        dao.observeAll().map { entries -> entries.map { it.toDomain() } }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    private companion object {
        /** S3112: the owner asked for "the last many measurements"; 500 rows is that, bounded. */
        const val MAX_HISTORY_ENTRIES = 500
    }
}
