package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.LauncherPinDao
import com.sza.fastmediasorter.data.local.db.LauncherPinEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherPinsRepositoryImplTest {

    @Test
    fun `concurrent adds keep both commands and ignore duplicates`() = runBlocking {
        val dao: LauncherPinDao = mockk()
        val rows = mutableMapOf<Int, LauncherPinEntity>()
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        every { dao.observeAll() } returns flow { emit(rows.values.sortedBy { it.position }) }
        coEvery { dao.upsert(any()) } coAnswers {
            val entity = firstArg<LauncherPinEntity>()
            if (rows.isEmpty()) {
                firstWriteStarted.complete(Unit)
                releaseFirstWrite.await()
            }
            rows[entity.position] = entity
        }
        val repository = LauncherPinsRepositoryImpl(dao)
        val first = LauncherCellCommand.Feature("first")
        val second = LauncherCellCommand.Feature("second")

        val firstAdd = async { repository.addPin(first) }
        firstWriteStarted.await()
        val secondAdd = async { repository.addPin(second) }
        releaseFirstWrite.complete(Unit)
        firstAdd.await()
        secondAdd.await()
        repository.addPin(first)

        assertEquals(listOf(0 to first, 1 to second), repository.observePins().first())
    }
}
