package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.domain.model.link.StreamingManifest

/**
 * S0003 — strategic §5.1 pillar D: candidate harvested from an HTML page.
 *
 * The [Source] enum order encodes the canonical tie-breaker priority used by
 * [CandidateSelectionPolicy]: structured data / oEmbed > og:* meta tags >
 * Twitter player > native media tags > srcset/img > standalone anchors.
 *
 * S0116 §5.1 pillar G: streaming manifests (HLS/DASH) are surfaced as additional
 * Source values; the parsed [manifest] reference is carried forward to the streaming
 * pipeline without re-detection.
 */
data class HtmlMediaCandidate(
    val url: String,
    val source: Source,
    val tentativeMime: String?,
    val tentativeSizeBytes: Long?,
    val manifest: StreamingManifest? = null,
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
        // S0116 §5.1 pillar G: streaming manifest variants.
        HLS_MANIFEST,
        DASH_MANIFEST,
    }
}
