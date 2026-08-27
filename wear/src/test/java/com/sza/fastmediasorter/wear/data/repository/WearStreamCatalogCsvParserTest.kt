package com.sza.fastmediasorter.wear.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearStreamCatalogCsvParserTest {

    private val parser = WearStreamCatalogCsvParser()

    @Test
    fun `parse empty text returns empty list`() {
        val entries = parser.parse("")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `parse valid csv returns entries`() {
        val csv = """
            name,url,media_kind,favicon_index,is_live
            Radio One,https://radio1.example.com/stream,AUDIO,0,true
            Video One,https://video1.example.com/live.m3u8,VIDEO,5,true
        """.trimIndent()

        val entries = parser.parse(csv)
        assertEquals(2, entries.size)

        assertEquals("Radio One", entries[0].name)
        assertEquals("https://radio1.example.com/stream", entries[0].url)
        assertEquals("AUDIO", entries[0].mediaKind)
        assertEquals(0, entries[0].faviconIndex)
        assertTrue(entries[0].isLive)

        assertEquals("Video One", entries[1].name)
        assertEquals("https://video1.example.com/live.m3u8", entries[1].url)
        assertEquals("VIDEO", entries[1].mediaKind)
        assertEquals(5, entries[1].faviconIndex)
        assertTrue(entries[1].isLive)
    }

    @Test
    fun `parse handles quoted cells and escaped quotes`() {
        val csv = """
            name,url,media_kind,notes
            "Radio, ""Rock"" Edition",https://rock.example.com/stream,AUDIO,"Music, news"
        """.trimIndent()

        val entries = parser.parse(csv)
        assertEquals(1, entries.size)
        assertEquals("Radio, \"Rock\" Edition", entries[0].name)
        assertEquals("https://rock.example.com/stream", entries[0].url)
        assertEquals("Music, news", entries[0].notes)
    }

    @Test
    fun `parse ignores rows with missing name or url`() {
        val csv = """
            name,url,media_kind
            ,https://noname.example.com,AUDIO
            NoUrl,,AUDIO
            Valid,https://valid.example.com,AUDIO
        """.trimIndent()

        val entries = parser.parse(csv)
        assertEquals(1, entries.size)
        assertEquals("Valid", entries[0].name)
    }

    @Test
    fun `parse invalid favicon index returns null`() {
        val csv = """
            name,url,favicon_index
            Radio,https://radio.example.com,invalid
            Radio2,https://radio2.example.com,-1
        """.trimIndent()

        val entries = parser.parse(csv)
        assertEquals(2, entries.size)
        assertNull(entries[0].faviconIndex)
        assertNull(entries[1].faviconIndex)
    }
}
