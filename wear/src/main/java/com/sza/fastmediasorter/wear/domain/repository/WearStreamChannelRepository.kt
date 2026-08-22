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

    /**
     * S1799: stores [channel], matching an existing row by `url` - the store is deduplicated by url.
     * Returns true when the channel was added, false when the existing row was replaced.
     */
    suspend fun upsertChannel(channel: WearStreamChannel): Boolean
}
