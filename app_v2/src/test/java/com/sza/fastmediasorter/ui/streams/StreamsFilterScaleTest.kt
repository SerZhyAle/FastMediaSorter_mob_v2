package com.sza.fastmediasorter.ui.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.SortMode
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.StreamsFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1502: a shape guard on the catalog pass at production scale, NOT a device measurement.
 *
 * The shipped catalog is ~20k rows and the filter re-runs on every keystroke, so an implementation that
 * is quadratic in the row count, or that allocates a fresh string per row per comparison, degrades from
 * "fine in a 3-row unit test" to "unusable on the floor device" without any test noticing. The ceiling
 * below is deliberately loose: a single linear pass over 20k rows clears it by more than an order of
 * magnitude even on a loaded CI machine, while a quadratic pass cannot reach it at all. It is a
 * regression tripwire on the algorithm's shape - the acceptance numbers for §11 come from a device run
 * recorded under `temp/S1502/`, and nothing here may be quoted as one.
 */
class StreamsFilterScaleTest {

    private fun catalog(size: Int): List<StreamSourceEntity> = (0 until size).map { index ->
        StreamSourceEntity(
            id = "s$index",
            url = "http://example/$index",
            title = "Channel $index ${TITLE_WORDS[index % TITLE_WORDS.size]}",
            mediaKind = MEDIA_KINDS[index % MEDIA_KINDS.size],
            sourceOrigin = "CATALOG",
            sortIndex = index,
            addedAt = index.toLong(),
            category = CATEGORIES[index % CATEGORIES.size],
            topic = TOPICS[index % TOPICS.size],
            language = LANGUAGES[index % LANGUAGES.size],
            country = COUNTRIES[index % COUNTRIES.size],
            pinned = false,
        )
    }

    @Test
    fun `filtering the full catalog stays within the linear-pass ceiling`() {
        val sources = catalog(CATALOG_ROWS)
        val filter = StreamsFilter(query = "channel 1", sort = SortMode.NAME)
        // Warm the JIT and the entity accessors so the measured run is not paying class-loading cost.
        StreamsViewModel.applyFilter(sources, filter)

        val startedAt = System.nanoTime()
        val result = StreamsViewModel.applyFilter(sources, filter)
        val elapsedMs = (System.nanoTime() - startedAt) / NANOS_PER_MS

        assertTrue(
            "the query must actually match rows, else the pass is not being exercised",
            result.unpinned.isNotEmpty(),
        )
        assertTrue(
            "filtering $CATALOG_ROWS rows took ${elapsedMs}ms, over the ${CEILING_MS}ms shape ceiling - " +
                "the pass is no longer a single linear walk, or it allocates per row",
            elapsedMs < CEILING_MS,
        )
    }

    @Test
    fun `a query matching only the last rows still finds them`() {
        // The ceiling above can be met by walking less of the catalog. This makes that cheat fail: the
        // sole match sits in the final thousand and matches on topic, which no head-only scan reaches.
        val sources = catalog(CATALOG_ROWS) + StreamSourceEntity(
            id = "needle",
            url = "http://example/needle",
            title = "Ordinary Title",
            mediaKind = "VIDEO",
            sourceOrigin = "CATALOG",
            sortIndex = CATALOG_ROWS,
            addedAt = CATALOG_ROWS.toLong(),
            category = "Live TV",
            topic = "Heliophysics",
            language = "english",
            country = "UA",
            pinned = false,
        )

        val result = StreamsViewModel.applyFilter(sources, StreamsFilter(query = "heliophysics"))

        assertEquals(listOf("needle"), result.unpinned.map { it.id })
    }

    private companion object {
        // Matches the shipped catalog's order of magnitude (19,860 rows as of S1476).
        const val CATALOG_ROWS = 20_000

        // Loose on purpose - see the class KDoc. A linear pass measures in single-digit milliseconds here.
        const val CEILING_MS = 2_000L
        const val NANOS_PER_MS = 1_000_000L

        val MEDIA_KINDS = listOf("VIDEO", "AUDIO", "VIDEO", "RTSP")
        val TITLE_WORDS = listOf("News", "Sport", "Kino", "Muzyka", "Pogoda", "Detskiy")
        val CATEGORIES = listOf("Live TV", "Radio", "Webcam")
        val TOPICS = listOf("News", "Sports", "Movies", "Music", "Documentary")
        val LANGUAGES = listOf("english", "russian,ukrainian", "german", "ukrainian")
        val COUNTRIES = listOf("UA", "DE", "PL", "US")
    }
}
