package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.ScheduledOperationDao
import com.sza.fastmediasorter.data.local.db.ScheduledOperationEntity
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledOperationRepositoryImpl @Inject constructor(
    private val dao: ScheduledOperationDao
) : ScheduledOperationRepository {

    override fun getAll(): Flow<List<ScheduledOperation>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllEnabled(): List<ScheduledOperation> =
        dao.getAllEnabled().map { it.toDomain() }

    override suspend fun getUpcomingEnabled(): List<ScheduledOperation> =
        dao.getUpcomingEnabled().map { it.toDomain() }

    override suspend fun getById(id: Long): ScheduledOperation? =
        dao.getById(id)?.toDomain()

    override suspend fun upsert(operation: ScheduledOperation): Long =
        dao.upsert(operation.toEntity())

    override suspend fun update(operation: ScheduledOperation) =
        dao.update(operation.toEntity())

    override suspend fun deleteById(id: Long) =
        dao.deleteById(id)

    override suspend fun deleteAll() =
        dao.deleteAll()

    override suspend fun getCount(): Int =
        dao.getCount()

    private fun ScheduledOperationEntity.toDomain() = ScheduledOperation(
        id = id,
        isEnabled = isEnabled,
        sourceResourceId = sourceResourceId,
        operationType = ScheduledOpType.valueOf(operationType),
        targetResourceId = targetResourceId,
        fileTypeMask = fileTypeMask,
        timeFilter = TimeFilter.valueOf(timeFilter),
        startTimeHour = startTimeHour,
        startTimeMinute = startTimeMinute,
        intervalHours = intervalHours,
        intervalMinutes = intervalMinutes,
        overwrite = overwrite,
        silentMode = silentMode,
        lastRunAt = lastRunAt,
        nextRunAt = nextRunAt,
        lastRunStatus = lastRunStatus,
        workerId = workerId
    )

    private fun ScheduledOperation.toEntity() = ScheduledOperationEntity(
        id = id,
        isEnabled = isEnabled,
        sourceResourceId = sourceResourceId,
        operationType = operationType.name,
        targetResourceId = targetResourceId,
        fileTypeMask = fileTypeMask,
        timeFilter = timeFilter.name,
        startTimeHour = startTimeHour,
        startTimeMinute = startTimeMinute,
        intervalHours = intervalHours,
        intervalMinutes = intervalMinutes,
        overwrite = overwrite,
        silentMode = silentMode,
        lastRunAt = lastRunAt,
        nextRunAt = nextRunAt,
        lastRunStatus = lastRunStatus,
        workerId = workerId
    )
}
