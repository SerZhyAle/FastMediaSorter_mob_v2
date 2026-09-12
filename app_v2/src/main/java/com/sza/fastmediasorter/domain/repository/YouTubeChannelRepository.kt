package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.youtube.YouTubeChannel
import com.sza.fastmediasorter.domain.model.youtube.YouTubeVideo

/**
 * S2032: the keyless lookup the launcher's YouTube window cell needs, as its own role.
 *
 * Strategic §5.3 keeps the lookup and embedding mechanism replaceable whole, without touching
 * installation, rendering or degradation - which only holds while every caller reaches it through this
 * interface rather than through the provider behind it.
 *
 * Both members answer null for every failure alike: an unknown channel, an unreachable network and a
 * malformed response are one answer to the caller, because the cell's reaction to all three is the same
 * message (strategic §11 criterion 7).
 */
interface YouTubeChannelRepository {

    /** Resolves a channel page address, an `@handle` or a bare channel ID into a titled channel. */
    suspend fun resolveChannel(query: String): YouTubeChannel?

    /** The channel's most recent upload, from its public feed. */
    suspend fun latestVideo(channelId: String): YouTubeVideo?
}
