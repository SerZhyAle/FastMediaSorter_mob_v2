package com.sza.fastmediasorter.wear.ui.player.common

import com.sza.fastmediasorter.wear.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import timber.log.Timber

/** What the phone is told a file plays from when it did not come off the network. */
private const val LOCAL_SOURCE_NAME = "Local"

/**
 * S2432: the payload both watch players push to the phone, built in one place.
 *
 * The source name is derived here rather than by each caller, because the phone's now-playing card
 * compares the two players' states and a screen inventing its own spelling of "where this came from"
 * is what makes one player's card disagree with the other's.
 */
internal fun wearPlaybackStatePayload(
    selected: SelectedMedia?,
    isPlaying: Boolean,
    fileName: String,
    positionMs: Long,
    durationMs: Long,
    mediaType: String
): WearPlaybackStatePayload {
    Timber.d("S2432: shared playback payload for $mediaType, playing=$isPlaying")
    return WearPlaybackStatePayload(
        isPlaying = isPlaying,
        fileName = fileName,
        sourceName = if (selected?.isNetworkSource == true) {
            selected.file.uri.host ?: ""
        } else {
            LOCAL_SOURCE_NAME
        },
        positionMs = positionMs,
        durationMs = durationMs,
        mediaType = mediaType
    )
}
