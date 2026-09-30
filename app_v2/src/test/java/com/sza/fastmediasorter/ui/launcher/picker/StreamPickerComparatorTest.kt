package com.sza.fastmediasorter.ui.launcher.picker

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.StreamDefaultSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StreamPickerComparatorTest {

    private val rows = listOf(
        entity(id = "1", title = "bravo", topic = "news", language = "uk", country = "UA", addedAt = 10),
        entity(id = "2", title = "Alpha", topic = "sport", language = "en", country = "GB", addedAt = 30),
        entity(id = "3", title = "charlie", topic = null, language = "de", country = null, addedAt = 20),
    )

    private fun order(sort: StreamDefaultSort) = rows.sortedWith(streamPickerComparator(sort)).map { it.id }

    @Test
    fun `name sort ignores case`() {
        assertEquals(listOf("2", "1", "3"), order(StreamDefaultSort.NAME))
    }

    @Test
    fun `topic sort puts a row without a topic last`() {
        assertEquals(listOf("1", "2", "3"), order(StreamDefaultSort.TOPIC))
    }

    @Test
    fun `language and country sorts follow their keys`() {
        assertEquals(listOf("3", "2", "1"), order(StreamDefaultSort.LANGUAGE))
        assertEquals(listOf("2", "1", "3"), order(StreamDefaultSort.COUNTRY))
    }

    @Test
    fun `recent sort shows the newest first`() {
        assertEquals(listOf("2", "3", "1"), order(StreamDefaultSort.RECENT))
    }

    @Test
    fun `changing the sort changes the order of the same filtered rows`() {
        assertNotEquals(order(StreamDefaultSort.NAME), order(StreamDefaultSort.RECENT))
    }

    @Test
    fun `a tie falls back to the title`() {
        val tied = listOf(
            entity(id = "b", title = "Zulu", topic = "news", language = null, country = null, addedAt = 1),
            entity(id = "a", title = "echo", topic = "News", language = null, country = null, addedAt = 1),
        )
        assertEquals(listOf("a", "b"), tied.sortedWith(streamPickerComparator(StreamDefaultSort.TOPIC)).map { it.id })
    }

    private fun entity(
        id: String,
        title: String,
        topic: String?,
        language: String?,
        country: String?,
        addedAt: Long,
    ) = StreamSourceEntity(
        id = id,
        url = "https://example.test/$id",
        title = title,
        mediaKind = "AUDIO",
        sourceOrigin = "IMPORTED",
        sortIndex = 0,
        addedAt = addedAt,
        topic = topic,
        language = language,
        country = country,
    )
}
