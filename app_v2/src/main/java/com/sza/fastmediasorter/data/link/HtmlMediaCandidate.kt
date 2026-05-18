package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.domain.model.link.StreamingManifest

/** Candidate harvested from a page-level extraction pass. */
data class HtmlMediaCandidate(
    val url: String,
    val source: Source,
    val tentativeMime: String?,
    val tentativeSizeBytes: Long?,
    val manifest: StreamingManifest? = null,
    /**
     * S0171: scheme+host of the page that produced this candidate (e.g. `https://www.instagram.com`).
     * Used to build the `Referer` header when re-fetching a signed CDN URL - Instagram's CDN edge
     * rejects the request without it. `null` for candidates whose origin couldn't be determined.
     */
    val pageOrigin: String? = null,
) {
    enum class Source {
        JSON_LD,
        OEMBED,
        OG_VIDEO,
        OG_IMAGE,
        TWITTER_PLAYER_STREAM,
        VIDEO_TAG,
        AUDIO_TAG,
        SOURCE_TAG,
        IMG_TAG,
        IMG_SRCSET,
        INLINE_LINK,
        HLS_MANIFEST,
        DASH_MANIFEST,
        EMBEDDED_JSON,
    }
}