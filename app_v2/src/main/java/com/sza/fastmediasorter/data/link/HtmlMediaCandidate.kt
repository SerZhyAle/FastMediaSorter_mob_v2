package com.sza.fastmediasorter.data.link

/**
 * S0003 — strategic §5.1 pillar D: candidate harvested from an HTML page.
 *
 * The [Source] enum order encodes the canonical tie-breaker priority used by
 * [CandidateSelectionPolicy]: og:* meta tags > Twitter player > native media tags >
 * srcset/img > standalone anchors.
 */
data class HtmlMediaCandidate(
    val url: String,
    val source: Source,
    val tentativeMime: String?,
    val tentativeSizeBytes: Long?,
) {
    enum class Source {
        OG_VIDEO,
        OG_IMAGE,
        TWITTER_PLAYER_STREAM,
        VIDEO_TAG,
        AUDIO_TAG,
        SOURCE_TAG,
        IMG_TAG,
        IMG_SRCSET,
        INLINE_LINK,
    }
}
