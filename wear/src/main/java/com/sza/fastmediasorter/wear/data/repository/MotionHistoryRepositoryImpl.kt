package com.sza.fastmediasorter.wear.data.repository

import com.sza.fastmediasorter.wear.data.db.MotionHistoryDao
import com.sza.fastmediasorter.wear.data.db.toDomain
import com.sza.fastmediasorter.wear.data.db.toEntity
import com.sza.fastmediasorter.wear.domain.model.MotionHistoryEntry
import com.sza.fastmediasorter.wear.domain.repository.MotionHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3014: Room-backed implementation of [MotionHistoryRepository].
 */
@Singleton
class MotionHistoryRepositoryImpl @Inject constructor(
    private val dao: MotionHistoryDao
) : MotionHistoryRepository {

    override fun observeAll(): Flow<List<MotionHistoryEntry>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun insert(entry: MotionHistoryEntry): Long =
        dao.insert(entry.toEntity())

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
