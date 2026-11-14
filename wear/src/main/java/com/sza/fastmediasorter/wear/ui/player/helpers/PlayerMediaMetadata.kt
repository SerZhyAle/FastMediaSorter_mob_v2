package com.sza.fastmediasorter.wear.ui.player.helpers

import androidx.media3.common.MediaMetadata
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile

/**
 * What the player and the media session show for [this] file: its tag title, else its name, with the artist and
 * the cover when the file has them. One mapping for every load path, so a first open, a page turn and a hand-back
 * describe the same track the same way.
 */
internal fun WearMediaFile.toPlayerMetadata(): MediaMetadata =
    MediaMetadata.Builder()
        .setTitle(title?.takeIf { it.isNotBlank() } ?: name)
        .setArtist(artist?.takeIf { it.isNotBlank() })
        .apply { albumArt?.let { setArtworkUri(it) } }
        .build()
