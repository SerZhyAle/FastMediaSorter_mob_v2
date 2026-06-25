package com.sza.fastmediasorter.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * S0668 / PHASE_01: the additive `favicon_index` column. Pure JVM (no Android), asserting the parse
 * tolerance that backs the backward/forward-compat invariant: a numeric index decodes; a blank,
 * non-numeric or negative cell -> null; an OLDER catalog with NO `favicon_index` column still parses
 * every row with `faviconIndex == null` and never throws (new-app-reads-old-catalog path).
 */
class StreamCatalogCsvParserFaviconTest {

    private lateinit var parser: StreamCatalogCsvParser

    @Before
    fun setUp() {
        parser = StreamCatalogCsvParser()
    }

    @Test
    fun `numeric favicon_index parses to that Int`() {
        val csv = "url,name,favicon_index\n" +
            "http://t1,Stream1,0\n" +
            "http://t2,Stream2,17"
        val results = parser.parse(csv)
        assertEquals(2, results.size)
        assertEquals(0, results[0].faviconIndex)
        assertEquals(17, results[1].faviconIndex)
    }

    @Test
    fun `blank favicon_index cell yields null`() {
        val csv = "url,name,favicon_index\n" +
            "http://t1,Stream1,"
        val results = parser.parse(csv)
        assertEquals(1, results.size)
        assertNull(results[0].faviconIndex)
    }

    @Test
    fun `non-numeric or negative favicon_index yields null`() {
        val csv = "url,name,favicon_index\n" +
            "http://t1,Stream1,abc\n" +
            "http://t2,Stream2,-3"
        val results = parser.parse(csv)
        assertEquals(2, results.size)
        assertNull(results[0].faviconIndex)
        assertNull(results[1].faviconIndex)
    }

    @Test
    fun `older catalog without favicon_index column parses every row with null and no exception`() {
        // No favicon_index header at all - the old-catalog shape. Must not throw and every row -> null.
        val csv = "url,name,category\n" +
            "http://t1,Stream1,News\n" +
            "http://t2,Stream2,Sports"
        val results = parser.parse(csv)
        assertEquals(2, results.size)
        assertNull(results[0].faviconIndex)
        assertNull(results[1].faviconIndex)
    }

    @Test
    fun `favicon_index read by header name survives column reorder`() {
        // The parser keys by header name, so a reordered column (favicon_index first) still resolves.
        val csv = "favicon_index,url,name\n" +
            "5,http://t1,Stream1"
        val results = parser.parse(csv)
        assertEquals(1, results.size)
        assertEquals(5, results[0].faviconIndex)
    }
}
