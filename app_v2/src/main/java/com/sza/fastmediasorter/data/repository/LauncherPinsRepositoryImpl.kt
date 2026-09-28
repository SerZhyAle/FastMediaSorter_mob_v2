package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.LauncherPinDao
import com.sza.fastmediasorter.data.local.db.LauncherPinEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LauncherPinsRepositoryImpl @Inject constructor(
    private val dao: LauncherPinDao,
) : LauncherPinsRepository {

    private val mutationMutex = Mutex()

    override fun observePins(): Flow<List<Pair<Int, LauncherCellCommand>>> =
        dao.observeAll()
            .map { rows ->
                rows.mapNotNull { row ->
                    LauncherCellCommand.decode(row.target)?.let { row.position to it }
                }
            }
            .distinctUntilChanged()

    override suspend fun addPin(command: LauncherCellCommand) = mutationMutex.withLock {
        val pins = observePins().first()
        if (pins.any { it.second == command }) return@withLock
        val used = pins.map { it.first }.toSet()
        var position = 0
        while (position in used) position++
        withContext(Dispatchers.IO) {
            dao.upsert(LauncherPinEntity(position = position, target = command.encode()))
        }
    }

    override suspend fun setPin(position: Int, command: LauncherCellCommand) {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                dao.upsert(LauncherPinEntity(position = position, target = command.encode()))
            }
        }
    }

    override suspend fun removePin(position: Int) {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                dao.deleteByPosition(position)
            }
        }
    }

    override suspend fun clearPins() {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                dao.deleteAll()
            }
        }
    }
}
