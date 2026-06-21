package com.sza.fastmediasorter.ui.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.MediaKindFilter
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.StreamsFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for the streams filter logic exercised through the ViewModel's `internal` companion
 * helpers, so no Hilt graph / coroutine dispatcher is needed.
 */
class StreamsFilterTest {

    private fun source(
        id: String,
        title: String = id,
        category: String? = null,
        language: String? = null,
        topic: String? = null,
        mediaKind: String = "VIDEO",
    ) = StreamSourceEntity(
        id = id,
        url = "http://example/$id",
        title = title,
        mediaKind = mediaKind,
        sourceOrigin = "CATALOG",
        sortIndex = 0,
        addedAt = 0L,
        category = category,
        topic = topic,
        language = language,
    )

    private fun ids(list: List<StreamSourceEntity>) = list.map { it.id }.toSet()

    @Test
    fun `multi-language cell matches single-language filter`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("multi", language = "russian,ukrainian"),
                source("eng", language = "english"),
            ),
            StreamsFilter(language = "ukrainian"),
        )
        assertEquals(setOf("multi"), ids(result))
    }

    @Test
    fun `null-language row stays visible under active language filter`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("nolang", language = null),
                source("ukr", language = "ukrainian"),
                source("rus", language = "russian"),
            ),
            StreamsFilter(language = "ukrainian"),
        )
        assertTrue("null-language row must remain visible", "nolang" in ids(result))
        assertTrue("matching-language row must remain", "ukr" in ids(result))
        assertFalse("non-matching-language row must drop", "rus" in ids(result))
    }

    @Test
    fun `both category and language must match (AND)`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("both", category = "Live TV", language = "ukrainian"),
                source("catOnly", category = "Live TV", language = "russian"),
                source("langOnly", category = "Radio", language = "ukrainian"),
            ),
            StreamsFilter(category = "Live TV", language = "ukrainian"),
        )
        assertEquals(setOf("both"), ids(result))
    }

    @Test
    fun `an unset facet passes everything`() {
        val sources = listOf(
            source("a", category = "Live TV", language = "russian"),
            source("b", category = "Radio", language = "ukrainian"),
        )
        // Only category set: the language facet is disabled.
        assertEquals(setOf("a"), ids(StreamsViewModel.applyFilter(sources, StreamsFilter(category = "Live TV"))))
        // No facet set: all rows pass.
        assertEquals(setOf("a", "b"), ids(StreamsViewModel.applyFilter(sources, StreamsFilter())))
    }

    @Test
    fun `media-kind filter folds RTSP into video and isolates audio`() {
        val sources = listOf(
            source("aud", mediaKind = "AUDIO"),
            source("vid", mediaKind = "VIDEO"),
            source("rtsp", mediaKind = "RTSP"),
        )
        assertEquals(
            setOf("vid", "rtsp"),
            ids(StreamsViewModel.applyFilter(sources, StreamsFilter(mediaKind = MediaKindFilter.VIDEO))),
        )
        assertEquals(
            setOf("aud"),
            ids(StreamsViewModel.applyFilter(sources, StreamsFilter(mediaKind = MediaKindFilter.AUDIO))),
        )
        assertEquals(
            setOf("aud", "vid", "rtsp"),
            ids(StreamsViewModel.applyFilter(sources, StreamsFilter(mediaKind = MediaKindFilter.ALL))),
        )
    }

    @Test
    fun `facetsOf splits comma-separated language cell into distinct facets`() {
        val facets = StreamsViewModel.facetsOf(
            listOf(
                source("a", language = "russian,ukrainian"),
                source("b", language = "ukrainian"),
            ),
        )
        assertEquals(listOf("russian", "ukrainian"), facets.languages)
    }
}
