package com.sza.fastmediasorter.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * S2669: the parse contract of the `collections.json` archive entry. The publisher already refuses a
 * malformed set, so what is proven here is that an unexpected payload never costs the caller the bank
 * that imported in the same run.
 *
 * Runs under Robolectric for the same reason `StructuredMediaSnifferTest` does (S0223): `org.json` is
 * a stub in a plain JVM unit test and every call would throw "not mocked".
 */
@RunWith(RobolectricTestRunner::class)
class StreamCollectionsJsonParserTest {

    private val parser = StreamCollectionsJsonParser()

    @Test
    fun `parses two collections and keeps the curator order`() {
        val result = parser.parse(VALID)

        assertEquals(2, result.collections.size)
        assertEquals(0, result.droppedCollections)
        val tv = result.collections.first { it.id == "tv-ru" }
        assertEquals(10, tv.sortOrder)
        assertEquals(listOf(URL_A, URL_B), tv.memberUrls)
        assertTrue("the locale map is stored verbatim", tv.namesJson.contains("\"uk\""))
    }

    @Test
    fun `one url belongs to two collections`() {
        val result = parser.parse(VALID)

        val holders = result.collections.filter { it.memberUrls.contains(URL_A) }.map { it.id }.sorted()

        assertEquals(listOf("news", "tv-ru"), holders)
    }

    @Test
    fun `an unknown schema version yields nothing rather than a guess`() {
        val result = parser.parse(VALID.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))

        assertEquals(emptyList<ParsedStreamCollection>(), result.collections)
    }

    @Test
    fun `a collection with no members is dropped and the rest survive`() {
        val result = parser.parse(
            """
            {"schemaVersion": 1, "collections": [
              {"id": "empty", "order": 1, "names": {"en": "Empty"}, "members": []},
              {"id": "tv-ru", "order": 2, "names": {"en": "Russian TV"}, "members": [{"url": "$URL_A", "order": 1}]}
            ]}
            """.trimIndent()
        )

        assertEquals(1, result.collections.size)
        assertEquals("tv-ru", result.collections.single().id)
        assertEquals(1, result.droppedCollections)
    }

    @Test
    fun `a collection missing its en name is dropped`() {
        val result = parser.parse(
            """
            {"schemaVersion": 1, "collections": [
              {"id": "tv-ru", "order": 1, "names": {"ru": "TV России"}, "members": [{"url": "$URL_A", "order": 1}]}
            ]}
            """.trimIndent()
        )

        assertEquals(emptyList<ParsedStreamCollection>(), result.collections)
        assertEquals(1, result.droppedCollections)
    }

    @Test
    fun `an unknown extra field is ignored rather than rejected`() {
        val result = parser.parse(
            """
            {"schemaVersion": 1, "generatedAt": "2026-09-08", "collections": [
              {"id": "tv-ru", "order": 1, "colour": "red", "names": {"en": "Russian TV"},
               "members": [{"url": "$URL_A", "order": 1, "note": "primary"}]}
            ]}
            """.trimIndent()
        )

        assertEquals(1, result.collections.size)
        assertEquals(listOf(URL_A), result.collections.single().memberUrls)
    }

    @Test
    fun `text that is not json is survived`() {
        val result = parser.parse("not json at all")

        assertEquals(emptyList<ParsedStreamCollection>(), result.collections)
    }

    private companion object {
        const val URL_A = "https://example.test/a.m3u8"
        const val URL_B = "https://example.test/b.m3u8"

        val VALID = """
            {"schemaVersion": 1, "collections": [
              {"id": "tv-ru", "order": 10,
               "names": {"en": "Russian TV", "ru": "TV России", "uk": "Телебачення Росії"},
               "members": [{"url": "$URL_A", "order": 1}, {"url": "$URL_B", "order": 2}]},
              {"id": "news", "order": 20,
               "names": {"en": "News", "ru": "Новости", "uk": "Новини"},
               "members": [{"url": "$URL_A", "order": 1}]}
            ]}
        """.trimIndent()
    }
}
