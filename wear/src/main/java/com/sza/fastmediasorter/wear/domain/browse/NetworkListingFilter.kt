package com.sza.fastmediasorter.wear.domain.browse

import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.asContentType
import com.sza.fastmediasorter.wear.domain.model.contentTypeForMime

/**
 * S2691: whether a file in a network directory listing belongs to the category the route asked for.
 *
 * The watch's own store is listed by category token - `BrowseViewModel.localSource` picks the query
 * from the token and never reads the route's [MediaType], which is why S2130's deliberate fallback
 * of an unmapped token to [MediaType.MUSIC] is harmless there. The network listing had no such
 * branch: its only filter tested three mime prefixes against that substituted media type, so a
 * source's Documents category opened a list of its audio files, and so did "all" on a share
 * configured to offer every file. This object gives the network path the token-first answer the
 * local path already had.
 *
 * It classifies nothing itself - `contentTypeForMime` is the module's one resolver (S2443's rule for
 * mime types, extended to documents by this ticket) - so a listing and the badge drawn on its rows
 * cannot disagree about what a file is.
 */
object NetworkListingFilter {

    /**
     * Whether a listing opened under [categoryToken] shows a file of [mimeType].
     *
     * [fallback] answers a token this vocabulary does not know, which preserves what the network
     * path did before this object existed: a route argument naming no category still opens a list
     * filtered by the media type `parseMediaType` derived from it.
     *
     * A file the resolver cannot place is out of every listing including "all": on a share that is
     * a directory entry or an archive, and putting it in a media list would offer the wearer a row
     * that opens nothing.
     */
    fun accepts(categoryToken: String?, mimeType: String?, fallback: MediaType): Boolean {
        val contentType = contentTypeForMime(mimeType)
        return when (categoryToken) {
            BrowseCategoryCatalog.TOKEN_MUSIC -> contentType == WearContentType.MUSIC
            BrowseCategoryCatalog.TOKEN_VIDEOS -> contentType == WearContentType.VIDEO
            BrowseCategoryCatalog.TOKEN_PHOTOS -> contentType == WearContentType.IMAGE
            BrowseCategoryCatalog.TOKEN_DOCUMENTS -> contentType == WearContentType.DOCUMENT
            BrowseCategoryCatalog.TOKEN_ALL,
            BrowseCategoryCatalog.TOKEN_BROWSE -> contentType != null

            else -> contentType == fallback.asContentType()
        }
    }
}
