package com.sza.fastmediasorter.wear.ui.player.common

import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.favoriteSourceId
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import timber.log.Timber

/** How a marked file or channel is addressed, whichever watch player opened it. */
internal data class WearFavoriteIdentity(val sourceId: String, val filePath: String)

/**
 * S2432: the one rule both watch players resolve a favourite mark by.
 *
 * S2039/S1954: a direct stream is addressed by its NORMALIZED url under the reserved stream source id,
 * so a channel reached through either player is one favourite and the streams list, which compares by
 * that form, actually finds it - the catalog row it was opened from does not survive a re-import while
 * the address does. S1846: everything else keeps the shared source-id rule and the path it already used.
 *
 * Which selection is passed in stays the caller's decision: the audio player prefers the selection it
 * remembered while paging, the video player asks the manager first, and aligning the two here would
 * change what the watch shows.
 */
internal fun resolveFavoriteIdentity(
    selected: SelectedMedia?,
    fallbackUri: String?
): WearFavoriteIdentity? {
    Timber.d("S2432: shared favourite identity, directStream=${selected?.isDirectStream}")
    if (selected != null && selected.isDirectStream) {
        return WearFavoriteIdentity(SOURCE_ID_STREAM, normalizeWearStreamUrl(selected.streamUri))
    }
    val path = selected?.streamUri ?: fallbackUri
    return path?.let {
        WearFavoriteIdentity(favoriteSourceId(selected?.isNetworkSource == true, selected?.sourceId), it)
    }
}
