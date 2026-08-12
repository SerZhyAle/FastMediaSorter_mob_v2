package com.sza.fastmediasorter.data.repository

import androidx.room.withTransaction
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.NetworkMeasurementDao
import com.sza.fastmediasorter.data.local.db.NetworkMeasurementEntity
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurementKind
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1433: Room-backed store for the manual-measurement history.
 *
 * The kind is persisted as the enum's name rather than its ordinal, so a row written by an older build
 * cannot be re-labelled by reordering the enum. The cost is that a name no longer in the enum has to be
 * handled on read, which [toDomainOrNull] does by dropping the row and saying so - the alternative,
 * mapping it onto some other kind, would put a wrong label in front of the user.
 */
@Singleton
class NetworkMeasurementHistoryRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: NetworkMeasurementDao,
) : NetworkMeasurementHistoryRepository {

    override suspend fun record(measurement: NetworkMeasurement) {
        // Insert and trim are one transaction: two measurements finishing within the same moment would
        // otherwise both insert before either trimmed, leaving the table over the cap until the next write.
        db.withTransaction {
            dao.insert(measurement.toEntity())
            dao.trimToNewest(MAX_STORED_MEASUREMENTS)
        }
    }

    // distinctUntilChanged because Room invalidates per table, not per row: record() writes twice in one
    // transaction (insert, then trim), and a trim that deleted nothing would otherwise re-emit an
    // identical list and re-render the history for no reason.
    override fun observeHistory(): Flow<List<NetworkMeasurement>> =
        dao.observeNewestFirst()
            .map { rows -> rows.toDomain() }
            .distinctUntilChanged()

    override suspend fun clear() = dao.deleteAll()

    override suspend fun exportPayload(): List<NetworkMeasurement> = observeHistory().first()

    private fun List<NetworkMeasurementEntity>.toDomain(): List<NetworkMeasurement> {
        val mapped = mapNotNull { it.toDomainOrNull() }
        val dropped = size - mapped.size
        if (dropped > 0) {
            Timber.w("Network history: dropped %d row(s) whose measurement kind is not known", dropped)
        }
        return mapped
    }

    private fun NetworkMeasurementEntity.toDomainOrNull(): NetworkMeasurement? {
        val parsedKind = NetworkMeasurementKind.entries.firstOrNull { it.name == kind } ?: return null
        return NetworkMeasurement(
            id = id,
            takenAtMillis = takenAtMillis,
            kind = parsedKind,
            networkLabel = networkLabel,
            downMbps = downMbps,
            upMbps = upMbps,
            latencyMs = latencyMs,
            resultText = resultText,
            succeeded = succeeded,
        )
    }

    private fun NetworkMeasurement.toEntity(): NetworkMeasurementEntity = NetworkMeasurementEntity(
        id = id,
        takenAtMillis = takenAtMillis,
        kind = kind.name,
        networkLabel = networkLabel,
        downMbps = downMbps,
        upMbps = upMbps,
        latencyMs = latencyMs,
        resultText = resultText,
        succeeded = succeeded,
    )

    private companion object {
        /** Strategic §3.2 caps the history by volume; 200 rows is deep enough to compare runs across days. */
        const val MAX_STORED_MEASUREMENTS = 200
    }
}
