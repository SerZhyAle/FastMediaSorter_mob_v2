package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.ScheduledOperation
import kotlinx.coroutines.flow.Flow

interface ScheduledOperationRepository {
    fun getAll(): Flow<List<ScheduledOperation>>
    suspend fun getAllEnabled(): List<ScheduledOperation>
    suspend fun getById(id: Long): ScheduledOperation?
    suspend fun upsert(operation: ScheduledOperation): Long
    suspend fun update(operation: ScheduledOperation)
    suspend fun deleteById(id: Long)
    suspend fun deleteAll()
    suspend fun getCount(): Int
}
