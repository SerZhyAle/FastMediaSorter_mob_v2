package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import kotlinx.coroutines.flow.Flow

/**
 * S1708: Repository interface for stream channels on Wear OS.
 */
interface WearStreamChannelRepository {
    suspend fun getAllChannels(): List<WearStreamChannel>
    fun observeChannels(): Flow<List<WearStreamChannel>>
    suspend fun saveChannels(channels: List<WearStreamChannel>)
    suspend fun clear()
}
