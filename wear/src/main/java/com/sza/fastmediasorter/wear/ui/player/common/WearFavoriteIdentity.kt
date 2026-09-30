package com.sza.fastmediasorter.wear.ui.player.common

import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.favoriteSourceId
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia

/** How a marked file or channel is addressed, whichever watch player opened it. */
internal data class WearFavoriteIdentity(val sourceId: String, val filePath: String)

/**
 * S2432: the one rule the watch players resolve a favourite mark by.
 *
 * S2039/S1954: a direct stream is addressed by its NORMALIZED url under the reserved stream source id,
 * so a channel reached through either player is one favourite and the streams list, which compares by
 * that form, actually finds it - the catalog row it was opened from does not survive a re-import while
 * the address does. S1846: everything else keeps the shared source-id rule and the path it already used.
 *
 * S3894: every caller passes the selection it remembered while paging before the one the screen was
 * opened with, and uses the result for both the read and the write of the mark. The manager answers
 * with the opened file for the whole life of the screen, so asking it first marked that file after
 * any page turn.
 */
internal fun resolveFavoriteIdentity(
    selected: SelectedMedia?,
    fallbackUri: String?
): WearFavoriteIdentity? {
    if (selected != null && selected.isDirectStream) {
        return WearFavoriteIdentity(SOURCE_ID_STREAM, normalizeWearStreamUrl(selected.streamUri))
    }
    val path = selected?.streamUri ?: fallbackUri
    return path?.let {
        WearFavoriteIdentity(favoriteSourceId(selected?.isNetworkSource == true, selected?.sourceId), it)
    }
}
