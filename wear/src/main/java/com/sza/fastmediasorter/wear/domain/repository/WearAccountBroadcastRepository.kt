package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearAccountBroadcastSection
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import kotlinx.coroutines.flow.Flow

/** S4229: the broadcasts of the account each of the watch's exchange entries belongs to (DEVICE-EXCHANGE item CI). */
interface WearAccountBroadcastRepository {

    /**
     * The list per entry, following the server while collected; nothing is requested when no one collects. Emits an
     * empty list when the watch has no entry.
     */
    fun observe(): Flow<List<WearAccountBroadcastSection>>

    /** Resolves one listed broadcast to something a player opens, judged from the directory record held now. */
    suspend fun resolve(entryIdentity: String, broadcastId: String): WearBroadcastPlayback
}
