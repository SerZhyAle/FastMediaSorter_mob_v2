package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.AppLaunchPanelTileDao
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTile
import com.sza.fastmediasorter.domain.repository.AppLaunchPanelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AppLaunchPanelRepositoryImpl @Inject constructor(
    private val dao: AppLaunchPanelTileDao
) : AppLaunchPanelRepository {

    override fun observeTiles(): Flow<List<AppLaunchPanelTile>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun setTile(tile: AppLaunchPanelTile) {
        dao.upsert(tile.toEntity())
    }

    override suspend fun removeTile(slotIndex: Int) {
        dao.deleteBySlot(slotIndex)
    }

    override suspend fun moveTile(fromSlot: Int, toSlot: Int) {
        if (fromSlot == toSlot) return
        val bySlot = dao.getAll().associateBy { it.slotIndex }
        val source = bySlot[fromSlot] ?: return
        val target = bySlot[toSlot]
        // Locked-view swap: source takes the target slot; any current target occupant falls back to
        // the source slot so every position stays explicitly assigned.
        dao.deleteBySlot(fromSlot)
        dao.deleteBySlot(toSlot)
        dao.upsert(source.copy(slotIndex = toSlot))
        if (target != null) {
            dao.upsert(target.copy(slotIndex = fromSlot))
        }
    }

    override suspend fun count(): Int = dao.count()

    override suspend fun replaceAll(tiles: List<AppLaunchPanelTile>) {
        dao.clearAll()
        tiles.forEach { dao.upsert(it.toEntity()) }
    }
}
