package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.ScheduledOperationDao
import com.sza.fastmediasorter.data.local.db.ScheduledOperationEntity
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.testing.createScheduledOperation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledOperationRepositoryImplTest {

    private lateinit var dao: ScheduledOperationDao
    private lateinit var repo: ScheduledOperationRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repo = ScheduledOperationRepositoryImpl(dao)
    }

    private fun entity() = ScheduledOperationEntity(
        id = 11L,
        isEnabled = true,
        sourceResourceId = 1L,
        operationType = "MOVE",
        targetResourceId = 2L,
        fileTypeMask = FileTypeFlags.DEFAULT,
        timeFilter = "LAST_DAY",
        startTimeHour = 3,
        startTimeMinute = 15,
        intervalHours = 4,
        intervalMinutes = 30,
        overwrite = true,
        silentMode = true,
        lastRunAt = 100L,
        nextRunAt = 200L,
        lastRunStatus = "OK",
        workerId = "w1",
    )

    @Test
    fun `getAll maps entity strings back to enums`() = runTest {
        every { dao.getAll() } returns flowOf(listOf(entity()))

        val op = repo.getAll().first().single()
        assertEquals(11L, op.id)
        assertEquals(ScheduledOpType.MOVE, op.operationType)
        assertEquals(TimeFilter.LAST_DAY, op.timeFilter)
        assertEquals(2L, op.targetResourceId)
        assertEquals(3, op.startTimeHour)
        assertEquals("OK", op.lastRunStatus)
    }

    @Test
    fun `getAllEnabled maps each entity`() = runTest {
        coEvery { dao.getAllEnabled() } returns listOf(entity())
        assertEquals(ScheduledOpType.MOVE, repo.getAllEnabled().single().operationType)
    }

    @Test
    fun `getById maps the entity`() = runTest {
        coEvery { dao.getById(11L) } returns entity()
        assertEquals(TimeFilter.LAST_DAY, repo.getById(11L)?.timeFilter)
    }

    @Test
    fun `getById returns null when dao has no row`() = runTest {
        coEvery { dao.getById(404L) } returns null
        assertNull(repo.getById(404L))
    }

    @Test
    fun `upsert maps domain enums to entity name strings`() = runTest {
        val captured = slot<ScheduledOperationEntity>()
        coEvery { dao.upsert(capture(captured)) } returns 42L

        val id = repo.upsert(
            createScheduledOperation(
                id = 5L,
                operationType = ScheduledOpType.DELETE,
                timeFilter = TimeFilter.ALL,
                targetResourceId = null,
            )
        )

        assertEquals(42L, id)
        assertEquals("DELETE", captured.captured.operationType)
        assertEquals("ALL", captured.captured.timeFilter)
        assertNull(captured.captured.targetResourceId)
    }

    @Test
    fun `update delegates with mapped entity`() = runTest {
        val captured = slot<ScheduledOperationEntity>()
        coEvery { dao.update(capture(captured)) } returns Unit

        repo.update(createScheduledOperation(id = 9L, operationType = ScheduledOpType.COPY))

        assertEquals(9L, captured.captured.id)
        assertEquals("COPY", captured.captured.operationType)
    }

    @Test
    fun `deleteById deleteAll and getCount delegate to dao`() = runTest {
        coEvery { dao.getCount() } returns 7

        repo.deleteById(3L)
        repo.deleteAll()
        assertEquals(7, repo.getCount())

        coVerify { dao.deleteById(3L) }
        coVerify { dao.deleteAll() }
    }
}
