package com.sza.fastmediasorter.data.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0166 keeps page-level selection deterministic so saved-auth retries do not
 * flip between preview assets and the real media candidate.
 */
class CandidateSelectionPolicyTest {

    @Test
    fun `returns null when all candidates are non-http`() {
        val out = CandidateSelectionPolicy.choose(
            listOf(candidate("blob:https://example.com/video", HtmlMediaCandidate.Source.VIDEO_TAG)),
        )

        assertNull(out)
    }

    @Test
    fun `prefers first candidate that reaches one mebibyte`() {
        val small = candidate(
            url = "https://cdn.example.com/preview.jpg",
            source = HtmlMediaCandidate.Source.OG_IMAGE,
            size = 120_000,
        )
        val large = candidate(
            url = "https://cdn.example.com/video.mp4",
            source = HtmlMediaCandidate.Source.VIDEO_TAG,
            size = 1_500_000,
        )
        val laterLarge = candidate(
            url = "https://cdn.example.com/video-2.mp4",
            source = HtmlMediaCandidate.Source.SOURCE_TAG,
            size = 9_000_000,
        )

        val out = CandidateSelectionPolicy.choose(listOf(small, large, laterLarge))

        assertEquals(large, out)
    }

    @Test
    fun `prefers manifest when sizes are unknown`() {
        val image = candidate("https://cdn.example.com/card.jpg", HtmlMediaCandidate.Source.OG_IMAGE)
        val manifest = candidate("https://cdn.example.com/master.m3u8", HtmlMediaCandidate.Source.HLS_MANIFEST)

        val out = CandidateSelectionPolicy.choose(listOf(image, manifest))

        assertEquals(manifest, out)
    }

    @Test
    fun `batch pool prefers authoritative embedded json candidates`() {
        val preview = candidate("https://cdn.example.com/preview.jpg", HtmlMediaCandidate.Source.OG_IMAGE)
        val embedded1 = candidate("https://cdn.example.com/slide-1.jpg", HtmlMediaCandidate.Source.EMBEDDED_JSON)
        val embedded2 = candidate("https://cdn.example.com/slide-2.jpg", HtmlMediaCandidate.Source.EMBEDDED_JSON)
        val sniffed = candidate("https://cdn.example.com/request-asset.jpg", HtmlMediaCandidate.Source.INLINE_LINK)

        val out = CandidateSelectionPolicy.batchPool(listOf(preview, embedded1, sniffed, embedded2))

        assertEquals(listOf(embedded1, embedded2), out)
    }

    @Test
    fun `batch pool falls back to http candidates when no embedded json is present`() {
        val nonHttp = candidate("blob:https://example.com/video", HtmlMediaCandidate.Source.VIDEO_TAG)
        val preview = candidate("https://cdn.example.com/preview.jpg", HtmlMediaCandidate.Source.OG_IMAGE)
        val direct = candidate("https://cdn.example.com/video.mp4", HtmlMediaCandidate.Source.VIDEO_TAG)

        val out = CandidateSelectionPolicy.batchPool(listOf(nonHttp, preview, direct))

        assertEquals(listOf(preview, direct), out)
    }

    /**
     * S0166 Phase 06: social-preview guard invariant.
     *
     * CandidateSelectionPolicy.choose() itself does not discriminate by source - that
     * responsibility sits in HtmlPageExtractionStrategy.  These tests document the
     * expected behaviour of the real-content predicate that guards the SocialPreviewOnly
     * branch: sources OG_IMAGE, IMG_TAG, and IMG_SRCSET are not treated as real content;
     * any other source is.
     */

    @Test
    fun `og_image only is not real content`() {
        val candidates = listOf(
            candidate("https://cdn.instagram.com/preview.jpg", HtmlMediaCandidate.Source.OG_IMAGE),
        )
        assertFalse(hasRealContent(candidates))
    }

    @Test
    fun `img_tag only is not real content`() {
        val candidates = listOf(
            candidate("https://cdn.example.com/thumb.jpg", HtmlMediaCandidate.Source.IMG_TAG),
        )
        assertFalse(hasRealContent(candidates))
    }

    @Test
    fun `img_srcset only is not real content`() {
        val candidates = listOf(
            candidate("https://cdn.example.com/thumb@2x.jpg", HtmlMediaCandidate.Source.IMG_SRCSET),
        )
        assertFalse(hasRealContent(candidates))
    }

    @Test
    fun `video_tag is real content`() {
        val candidates = listOf(
            candidate("https://cdn.instagram.com/video.mp4", HtmlMediaCandidate.Source.VIDEO_TAG),
        )
        assertTrue(hasRealContent(candidates))
    }

    @Test
    fun `og_image plus video_tag is real content`() {
        val candidates = listOf(
            candidate("https://cdn.instagram.com/preview.jpg", HtmlMediaCandidate.Source.OG_IMAGE),
            candidate("https://cdn.instagram.com/video.mp4", HtmlMediaCandidate.Source.VIDEO_TAG),
        )
        assertTrue(hasRealContent(candidates))
    }

    @Test
    fun `json_ld source is real content`() {
        val candidates = listOf(
            candidate("https://cdn.example.com/media.mp4", HtmlMediaCandidate.Source.JSON_LD),
        )
        assertTrue(hasRealContent(candidates))
    }

    @Test
    fun `hls_manifest is real content`() {
        val candidates = listOf(
            candidate("https://cdn.example.com/stream.m3u8", HtmlMediaCandidate.Source.HLS_MANIFEST),
        )
        assertTrue(hasRealContent(candidates))
    }

    /**
     * Mirrors the predicate in HtmlPageExtractionStrategy that classifies a candidate
     * list as preview-only (not real content) for preview-sensitive hosts.
     *
     * Sources considered *not* real content: OG_IMAGE, IMG_TAG, IMG_SRCSET.
     * Any other source means the page has real media.
     */
    private fun hasRealContent(candidates: List<HtmlMediaCandidate>): Boolean =
        candidates.any { c ->
            c.source != HtmlMediaCandidate.Source.OG_IMAGE &&
                c.source != HtmlMediaCandidate.Source.IMG_TAG &&
                c.source != HtmlMediaCandidate.Source.IMG_SRCSET
        }

    private fun candidate(
        url: String,
        source: HtmlMediaCandidate.Source,
        size: Long? = null,
    ): HtmlMediaCandidate = HtmlMediaCandidate(
        url = url,
        source = source,
        tentativeMime = null,
        tentativeSizeBytes = size,
    )
}