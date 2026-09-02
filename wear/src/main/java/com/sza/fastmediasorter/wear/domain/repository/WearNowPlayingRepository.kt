package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearNowPlaying
import kotlinx.coroutines.flow.Flow

/**
 * S2047: repository managing watch now-playing / last-played state for complication data sources.
 */
interface WearNowPlayingRepository {

    /** Observed stream of current or last-played track info. */
    val nowPlaying: Flow<WearNowPlaying>

    /** Updates title and optional subtitle when a new media item starts playing. */
    suspend fun setNowPlaying(title: String, subtitle: String?)

    /** Updates the live playback flag when playback pauses or resumes. */
    suspend fun setPlaying(isPlaying: Boolean)

    /**
     * Resets the [WearNowPlaying.isPlaying] flag to false while preserving the track metadata.
     * S2047: Strategic ADR-2 requires clearing this flag in onCleared and on process start
     * to avoid stale playing indicators.
     */
    suspend fun clearPlayingFlag()
}
