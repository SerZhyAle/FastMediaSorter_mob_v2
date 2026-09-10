package com.sza.fastmediasorter.wear.data.repository

import com.sza.fastmediasorter.wear.data.db.BloodPressureHistoryDao
import com.sza.fastmediasorter.wear.data.db.BloodPressureHistoryEntity
import com.sza.fastmediasorter.wear.data.db.toDomain
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry
import com.sza.fastmediasorter.wear.domain.repository.BloodPressureHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S2809: Room-backed implementation of [BloodPressureHistoryRepository].
 *
 * The timestamp is stamped at save time so the ViewModel does not need to pass it,
 * and the entity-to-domain mapping keeps the Room type out of the UI layer.
 */
class BloodPressureHistoryRepositoryImpl @Inject constructor(
    private val dao: BloodPressureHistoryDao
) : BloodPressureHistoryRepository {

    override suspend fun save(systolic: Int, diastolic: Int) {
        dao.insert(
            BloodPressureHistoryEntity(
                systolic = systolic,
                diastolic = diastolic,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    override fun observeAll(): Flow<List<BloodPressureHistoryEntry>> =
        dao.observeAll().map { entries -> entries.map { it.toDomain() } }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
