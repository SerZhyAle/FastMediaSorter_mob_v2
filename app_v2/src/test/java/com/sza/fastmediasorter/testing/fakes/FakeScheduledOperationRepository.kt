package com.sza.fastmediasorter.testing.fakes

import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written fake for [ScheduledOperationRepository].
 *
 * Backing state is an in-memory list exposed via [operationsFlow]. `upsert` assigns an id when the
 * incoming operation has id == 0L. Mutations are recorded for assertions. No real I/O, no logging.
 */
class FakeScheduledOperationRepository : ScheduledOperationRepository {

    private val operationsFlow = MutableStateFlow<List<ScheduledOperation>>(emptyList())

    /** Snapshot of the current operations. */
    val operations: List<ScheduledOperation>
        get() = operationsFlow.value

    // Invocation records ---------------------------------------------------
    val upsertedOperations: MutableList<ScheduledOperation> = mutableListOf()
    val updatedOperations: MutableList<ScheduledOperation> = mutableListOf()
    val deletedIds: MutableList<Long> = mutableListOf()
    var deleteAllCalled: Boolean = false
    var nextInsertId: Long = 1L

    /** Seed the backing list directly. */
    fun setOperations(items: List<ScheduledOperation>) {
        operationsFlow.value = items
    }

    override fun getAll(): Flow<List<ScheduledOperation>> = operationsFlow

    override suspend fun getAllEnabled(): List<ScheduledOperation> =
        operationsFlow.value.filter { it.isEnabled }

    override suspend fun getById(id: Long): ScheduledOperation? =
        operationsFlow.value.firstOrNull { it.id == id }

    override suspend fun upsert(operation: ScheduledOperation): Long {
        upsertedOperations.add(operation)
        val id = if (operation.id != 0L) operation.id else nextInsertId++
        val stored = operation.copy(id = id)
        operationsFlow.value = operationsFlow.value.filterNot { it.id == id } + stored
        return id
    }

    override suspend fun update(operation: ScheduledOperation) {
        updatedOperations.add(operation)
        operationsFlow.value = operationsFlow.value.map {
            if (it.id == operation.id) operation else it
        }
    }

    override suspend fun deleteById(id: Long) {
        deletedIds.add(id)
        operationsFlow.value = operationsFlow.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        deleteAllCalled = true
        operationsFlow.value = emptyList()
    }

    override suspend fun getCount(): Int = operationsFlow.value.size
}
