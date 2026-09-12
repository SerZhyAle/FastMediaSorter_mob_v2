package com.sza.fastmediasorter.wear.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2669: the watch-side mirror of the phone parser test - the same six cases over the same payload
 * text, because the two parsers live in different modules and nothing else forces them to agree
 * (strategic risk table). A future change to the shape must break both tests or neither.
 *
 * No Robolectric here, unlike the phone twin: the watch parser reads through Gson, which is a plain
 * JVM dependency on both sides of the test boundary.
 */
class WearStreamCollectionsJsonParserTest {

    private val parser = WearStreamCollectionsJsonParser()

    @Test
    fun `parses two collections and keeps the curator order`() {
        val result = parser.parse(VALID)

        assertEquals(2, result.size)
        val tv = result.first { it.id == "tv-ru" }
        assertEquals(10, tv.sortOrder)
        assertEquals(listOf(URL_A, URL_B), tv.memberUrls)
        assertTrue("the locale map is open, not reduced to the interface locales", tv.names.containsKey("uk"))
    }

    @Test
    fun `one url belongs to two collections`() {
        val result = parser.parse(VALID)

        val holders = result.filter { it.memberUrls.contains(URL_A) }.map { it.id }.sorted()

        assertEquals(listOf("news", "tv-ru"), holders)
    }

    @Test
    fun `an unknown schema version yields nothing rather than a guess`() {
        val result = parser.parse(VALID.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))

        assertEquals(emptyList<String>(), result.map { it.id })
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

        assertEquals(listOf("tv-ru"), result.map { it.id })
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

        assertEquals(emptyList<String>(), result.map { it.id })
    }

    @Test
    fun `an unknown extra field is ignored rather than rejected`() {
        val result = parser.parse(
            """
            {"schemaVersion": 1, "generatedAt": "2026-09-09", "collections": [
              {"id": "tv-ru", "order": 1, "colour": "red", "names": {"en": "Russian TV"},
               "members": [{"url": "$URL_A", "order": 1, "note": "primary"}]}
            ]}
            """.trimIndent()
        )

        assertEquals(listOf(URL_A), result.single().memberUrls)
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
