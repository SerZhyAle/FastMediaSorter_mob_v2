package com.sza.fastmediasorter.ui.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.FilteredStreams
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.MediaKindFilter
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.SortMode
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

    @Suppress("LongParameterList")
    private fun source(
        id: String,
        title: String = id,
        category: String? = null,
        language: String? = null,
        country: String? = null,
        topic: String? = null,
        mediaKind: String = "VIDEO",
        pinned: Boolean = false,
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
        country = country,
        pinned = pinned,
    )

    /**
     * S1502: `applyFilter` hands back the two halves separately. The flat, pinned-first list it used to
     * return is exactly this concatenation, so every ordering assertion below still reads the same order
     * the screen renders.
     */
    private fun ordered(result: FilteredStreams) = result.pinned + result.unpinned

    private fun ids(result: FilteredStreams) = ordered(result).map { it.id }.toSet()

    @Test
    fun `pinnedOnly keeps only pinned rows`() {
        val sources = listOf(
            source("p1", pinned = true),
            source("p2", pinned = true),
            source("u1", pinned = false),
        )
        assertEquals(
            setOf("p1", "p2"),
            ids(StreamsViewModel.applyFilter(sources, StreamsFilter(pinnedOnly = true))),
        )
        // Off by default: every row passes.
        assertEquals(
            setOf("p1", "p2", "u1"),
            ids(StreamsViewModel.applyFilter(sources, StreamsFilter(pinnedOnly = false))),
        )
    }

    @Test
    fun `multi-language cell matches single-language filter`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("multi", language = "english,ukrainian"),
                source("eng", language = "english"),
            ),
            StreamsFilter(language = "ukrainian"),
        )
        assertEquals(setOf("multi"), ids(result))
    }

    @Test
    fun `null-language row is hidden under active language filter`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("nolang", language = null),
                source("ukr", language = "ukrainian"),
                source("rus", language = "russian"),
            ),
            StreamsFilter(language = "ukrainian"),
        )
        assertFalse("null-language row must be hidden", "nolang" in ids(result))
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
    fun `topic alone isolates the sources carrying it`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("cam1", category = "Live TV", topic = "Webcam"),
                source("cam2", category = "Live TV", topic = "Webcam"),
                source("channel", category = "Live TV", topic = "News"),
                source("untagged", category = "Live TV"),
            ),
            StreamsFilter(topic = "Webcam"),
        )
        assertEquals(setOf("cam1", "cam2"), ids(result))
    }

    @Test
    fun `both category and topic must match (AND)`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("both", category = "Live TV", topic = "Webcam"),
                source("catOnly", category = "Live TV", topic = "News"),
                source("topicOnly", category = "Radio", topic = "Webcam"),
            ),
            StreamsFilter(category = "Live TV", topic = "Webcam"),
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

    @Test
    fun `facetsOf collects country as single-value distinct sorted codes`() {
        val facets = StreamsViewModel.facetsOf(
            listOf(
                source("a", country = "UA"),
                source("b", country = "DE"),
                source("c", country = "UA"),
                source("d", country = null),
            ),
        )
        // Single code per row (no comma split), de-duplicated and sorted; null country is dropped.
        assertEquals(listOf("DE", "UA"), facets.countries)
    }

    @Test
    fun `active country filter excludes unknown-country rows`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("ua", country = "UA"),
                source("de", country = "DE"),
                source("unknown", country = null),
            ),
            StreamsFilter(country = "UA"),
        )
        assertEquals(setOf("ua"), ids(result))
    }

    @Test
    fun `pinned rows keep manual order while unpinned follow the chosen sort`() {
        // S0938: pinned block preserves its incoming (sortIndex) order; only unpinned rows sort by NAME.
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("p2", title = "B", pinned = true),
                source("p1", title = "A", pinned = true),
                source("u2", title = "D", pinned = false),
                source("u1", title = "C", pinned = false),
            ),
            StreamsFilter(sort = SortMode.NAME),
        )
        assertEquals(listOf("p2", "p1", "u1", "u2"), ordered(result).map { it.id })
    }

    @Test
    fun `country sort places unknown-country rows last`() {
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("unknown", country = null),
                source("ua", country = "UA"),
                source("de", country = "DE"),
            ),
            StreamsFilter(sort = SortMode.COUNTRY),
        )
        // nullsLast: DE, UA, then the unknown-country row.
        assertEquals(listOf("de", "ua", "unknown"), ordered(result).map { it.id })
    }

    @Test
    fun `applyFilter is pure - repeatable result and untouched input`() {
        // S1502: the pass now runs on a background dispatcher (StreamsViewModel init, flowOn). That is
        // only safe while applyFilter stays a pure function of its arguments, so purity is pinned here
        // rather than left as an assumption the next edit can silently break.
        val input = listOf(
            source("p1", title = "B", pinned = true),
            source("u2", title = "D"),
            source("u1", title = "C"),
        )
        val snapshotOfInput = input.map { it.id }
        val filter = StreamsFilter(sort = SortMode.NAME)

        val first = StreamsViewModel.applyFilter(input, filter)
        val second = StreamsViewModel.applyFilter(input, filter)

        assertEquals(ordered(first).map { it.id }, ordered(second).map { it.id })
        assertEquals(snapshotOfInput, input.map { it.id })
    }

    @Test
    fun `query matches title, topic and language whatever the case on either side`() {
        // S1502: matching folds case per comparison instead of lowercasing both sides. That swap is
        // exactly the kind of edit that can silently narrow search, so each field is pinned with the
        // query and the row disagreeing on case - strategic §11 criterion 8.
        val sources = listOf(
            source("byTitle", title = "Euronews HD"),
            source("byTopic", title = "zzz", topic = "Documentary"),
            source("byLanguage", title = "yyy", language = "Ukrainian"),
            source("miss", title = "xxx", topic = "Music", language = "german"),
        )

        assertEquals(setOf("byTitle"), ids(StreamsViewModel.applyFilter(sources, StreamsFilter(query = "EURONEWS"))))
        assertEquals(setOf("byTitle"), ids(StreamsViewModel.applyFilter(sources, StreamsFilter(query = "euronews"))))
        assertEquals(setOf("byTopic"), ids(StreamsViewModel.applyFilter(sources, StreamsFilter(query = "dOcUmEnT"))))
        assertEquals(setOf("byLanguage"), ids(StreamsViewModel.applyFilter(sources, StreamsFilter(query = "UKRAIN"))))
        assertTrue(
            "a non-matching row must stay out regardless of case folding",
            "miss" !in ids(StreamsViewModel.applyFilter(sources, StreamsFilter(query = "euronews"))),
        )
    }

    @Test
    fun `query reaches an unpinned row at the far end of the catalog`() {
        // S1502: guards against a future paging/truncation change satisfying the filter by only walking
        // the head of the catalog - the sole match here is last, unpinned, and matches by topic only.
        val filler = (0 until FAR_END_ROWS).map { source("filler$it", title = "Channel $it", topic = "News") }
        val needle = source("needle", title = "Nothing In Common", topic = "Astronomy")

        val result = StreamsViewModel.applyFilter(filler + needle, StreamsFilter(query = "astronomy"))

        assertEquals(setOf("needle"), ids(result))
        assertTrue("a match found only by search must not be promoted into the pinned half", result.pinned.isEmpty())
    }

    @Test
    fun `the two halves concatenate back to the flat pinned-first ordering`() {
        // S1502: the split moved out of the consumers and into applyFilter. This pins the contract they
        // now rely on - pinned first in manual order, unpinned after in the chosen sort, no row in both.
        val result = StreamsViewModel.applyFilter(
            listOf(
                source("u2", title = "D"),
                source("p2", title = "B", pinned = true),
                source("u1", title = "C"),
                source("p1", title = "A", pinned = true),
            ),
            StreamsFilter(sort = SortMode.NAME),
        )

        assertEquals(listOf("p2", "p1"), result.pinned.map { it.id })
        assertEquals(listOf("u1", "u2"), result.unpinned.map { it.id })
        assertEquals(listOf("p2", "p1", "u1", "u2"), ordered(result).map { it.id })
        val pinnedIds = result.pinned.map { it.id }.toSet()
        val unpinnedIds = result.unpinned.map { it.id }.toSet()
        assertTrue("a row is pinned XOR unpinned", (pinnedIds intersect unpinnedIds).isEmpty())
    }

    private companion object {
        // Large enough that a head-only scan would miss the needle, small enough to stay a unit test.
        const val FAR_END_ROWS = 500
    }
}
