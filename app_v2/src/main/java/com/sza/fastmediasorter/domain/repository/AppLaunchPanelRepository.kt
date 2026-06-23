package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.AppLaunchPanelTile
import kotlinx.coroutines.flow.Flow

/**
 * S0623: persistence facade for the app-launch panel tile configuration. Slots are a fixed 0..14
 * locked view; [moveTile] reassigns positions rather than reordering a free list.
 */
interface AppLaunchPanelRepository {
    fun observeTiles(): Flow<List<AppLaunchPanelTile>>
    suspend fun setTile(tile: AppLaunchPanelTile)
    suspend fun removeTile(slotIndex: Int)
    suspend fun moveTile(fromSlot: Int, toSlot: Int)
    suspend fun count(): Int
    suspend fun replaceAll(tiles: List<AppLaunchPanelTile>)
}
