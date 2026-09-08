package com.sza.fastmediasorter.ui.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.FilteredStreams
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.SortMode
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.StreamsFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2669: the curated-collection condition, exercised through the ViewModel's `internal` companion
 * helpers so no Hilt graph or dispatcher is needed.
 *
 * Every case here is one of the ticket's acceptance criteria: a collection narrows the list, the other
 * filters keep working inside it, an explicit sort beats the curator, and membership is a many-to-many
 * relation rather than a partition of the catalog.
 */
class StreamsCollectionFilterTest {

    private fun source(id: String, title: String = id, pinned: Boolean = false) = StreamSourceEntity(
        id = id,
        url = "http://example/$id",
        title = title,
        mediaKind = "VIDEO",
        sourceOrigin = "CATALOG",
        sortIndex = 0,
        addedAt = 0L,
        category = null,
        topic = null,
        language = null,
        country = null,
        pinned = pinned,
    )

    private fun urlOf(id: String) = "http://example/$id"

    private fun ordered(result: FilteredStreams) = (result.pinned + result.unpinned).map { it.id }

    private val catalog = listOf(
        source("zebra", title = "Zebra"),
        source("alpha", title = "Alpha"),
        source("outsider", title = "Outsider"),
    )

    /** Curator order deliberately disagrees with the alphabet: Zebra first, Alpha second. */
    private val members = mapOf(urlOf("zebra") to 1, urlOf("alpha") to 2)

    private fun selected(
        query: String = "",
        orderApplied: Boolean = true,
        sort: SortMode = SortMode.NAME,
    ) = StreamsFilter(
        query = query,
        sort = sort,
        collectionId = "ru-tv",
        collectionMemberOrder = members,
        collectionOrderApplied = orderApplied,
    )

    @Test
    fun `selecting a collection keeps only its members`() {
        val result = StreamsViewModel.applyFilter(catalog, selected())
        assertEquals(listOf("zebra", "alpha"), ordered(result))
    }

    @Test
    fun `curator order differs from alphabetical and wins`() {
        val result = StreamsViewModel.applyFilter(catalog, selected())
        // Alphabetically Alpha precedes Zebra; the curator put Zebra first, and that is what renders.
        assertEquals(listOf("zebra", "alpha"), ordered(result))
    }

    @Test
    fun `an explicit sort overrides the curator order`() {
        val result = StreamsViewModel.applyFilter(
            catalog,
            selected(orderApplied = false, sort = SortMode.NAME),
        )
        assertEquals(listOf("alpha", "zebra"), ordered(result))
    }

    @Test
    fun `the search query narrows further inside a collection`() {
        val result = StreamsViewModel.applyFilter(catalog, selected(query = "alp"))
        assertEquals(listOf("alpha"), ordered(result))
    }

    @Test
    fun `a stream belonging to two collections appears under both`() {
        val shared = mapOf(urlOf("alpha") to 1)
        val first = StreamsViewModel.applyFilter(
            catalog,
            StreamsFilter(collectionId = "ru-tv", collectionMemberOrder = shared, collectionOrderApplied = true),
        )
        val second = StreamsViewModel.applyFilter(
            catalog,
            StreamsFilter(collectionId = "radio", collectionMemberOrder = shared, collectionOrderApplied = true),
        )
        assertEquals(listOf("alpha"), ordered(first))
        assertEquals(listOf("alpha"), ordered(second))
    }

    @Test
    fun `clearing the selection restores the whole catalog`() {
        val result = StreamsViewModel.applyFilter(catalog, StreamsFilter())
        assertEquals(listOf("alpha", "outsider", "zebra"), ordered(result))
    }

    @Test
    fun `pinned rows keep their own section above the curator order`() {
        val withPinned = catalog + source("pinned", title = "Pinned", pinned = true)
        val result = StreamsViewModel.applyFilter(
            withPinned,
            StreamsFilter(
                collectionId = "ru-tv",
                collectionMemberOrder = members + (urlOf("pinned") to 3),
                collectionOrderApplied = true,
            ),
        )
        assertEquals(listOf("pinned", "zebra", "alpha"), ordered(result))
    }
}
