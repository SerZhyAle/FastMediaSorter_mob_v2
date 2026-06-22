package com.sza.fastmediasorter.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamCatalogCsvParserTest {

    private lateinit var parser: StreamCatalogCsvParser

    @Before
    fun setUp() {
        parser = StreamCatalogCsvParser()
    }

    @Test
    fun testBasicParsing() {
        val csv = "url,name,category,topic\n" +
                "http://test.com/stream1.m3u8,Test Stream 1,News,Local\n" +
                "http://test.com/stream2.m3u8,Test Stream 2,Sports,Soccer"
        val results = parser.parse(csv)
        assertEquals(2, results.size)
        
        assertEquals("http://test.com/stream1.m3u8", results[0].url)
        assertEquals("Test Stream 1", results[0].name)
        assertEquals("News", results[0].category)
        assertEquals("Local", results[0].topic)

        assertEquals("http://test.com/stream2.m3u8", results[1].url)
        assertEquals("Test Stream 2", results[1].name)
        assertEquals("Sports", results[1].category)
        assertEquals("Soccer", results[1].topic)
    }

    @Test
    fun testQuotedFieldWithComma() {
        val csv = "url,name,category\n" +
                "http://test.com/stream,\"Test, Stream\",Category1"
        val results = parser.parse(csv)
        assertEquals(1, results.size)
        assertEquals("Test, Stream", results[0].name)
    }

    @Test
    fun testEscapedQuotes() {
        val csv = "url,name,category\n" +
                "http://test.com/stream,\"Test \"\"Special\"\" Stream\",Category1"
        val results = parser.parse(csv)
        assertEquals(1, results.size)
        assertEquals("Test \"Special\" Stream", results[0].name)
    }

    @Test
    fun testEmbeddedNewlines() {
        val csv = "url,name,notes\n" +
                "http://test.com/stream,Test Stream,\"Line1\nLine2\r\nLine3\""
        val results = parser.parse(csv)
        assertEquals(1, results.size)
        assertEquals("Line1\nLine2\r\nLine3", results[0].notes)
    }

    @Test
    fun testLineEndingsLfAndCrlf() {
        // LF endings
        val csvLf = "url,name\nhttp://test1,Stream1\nhttp://test2,Stream2"
        val resultsLf = parser.parse(csvLf)
        assertEquals(2, resultsLf.size)
        assertEquals("Stream1", resultsLf[0].name)
        assertEquals("Stream2", resultsLf[1].name)

        // CRLF endings
        val csvCrlf = "url,name\r\nhttp://test1,Stream1\r\nhttp://test2,Stream2"
        val resultsCrlf = parser.parse(csvCrlf)
        assertEquals(2, resultsCrlf.size)
        assertEquals("Stream1", resultsCrlf[0].name)
        assertEquals("Stream2", resultsCrlf[1].name)
    }

    @Test
    fun testTrailingNewline() {
        val csvWithTrailingLf = "url,name\nhttp://test1,Stream1\n"
        val resultsLf = parser.parse(csvWithTrailingLf)
        assertEquals(1, resultsLf.size)

        val csvWithTrailingCrlf = "url,name\r\nhttp://test1,Stream1\r\n"
        val resultsCrlf = parser.parse(csvWithTrailingCrlf)
        assertEquals(1, resultsCrlf.size)
    }

    @Test
    fun testReorderedColumns() {
        val csv = "name,category,url\nStream1,Category1,http://test1"
        val results = parser.parse(csv)
        assertEquals(1, results.size)
        assertEquals("Stream1", results[0].name)
        assertEquals("Category1", results[0].category)
        assertEquals("http://test1", results[0].url)
    }

    @Test
    fun testMissingOptionalColumns() {
        val csv = "url,name\nhttp://test1,Stream1"
        val results = parser.parse(csv)
        assertEquals(1, results.size)
        assertEquals("Stream1", results[0].name)
        assertEquals("http://test1", results[0].url)
        assertEquals("", results[0].category)
        assertFalse(results[0].isLive)
        assertFalse(results[0].https)
    }

    @Test
    fun testBlankRequiredFieldsSkipped() {
        val csv = "url,name\n" +
                ",Stream1\n" + // empty url
                "http://test2,\n" + // empty name
                "   ,   \n" + // whitespace only
                "http://test4,Stream4"
        val results = parser.parse(csv)
        assertEquals(1, results.size)
        assertEquals("Stream4", results[0].name)
        assertEquals("http://test4", results[0].url)
    }

    @Test
    fun testBooleanFlagParsing() {
        val csv = "url,name,is_live,https\n" +
                "http://t1,S1,true,true\n" +
                "http://t2,S2,TRUE,TRUE\n" +
                "http://t3,S3,  true  ,  TRUE  \n" +
                "http://t4,S4,false,false\n" +
                "http://t5,S5,1,yes\n" +
                "http://t6,S6,," // empty
        val results = parser.parse(csv)
        assertEquals(6, results.size)

        assertTrue(results[0].isLive)
        assertTrue(results[0].https)

        assertTrue(results[1].isLive)
        assertTrue(results[1].https)

        assertTrue(results[2].isLive)
        assertTrue(results[2].https)

        assertFalse(results[3].isLive)
        assertFalse(results[3].https)

        assertFalse(results[4].isLive)
        assertFalse(results[4].https)

        assertFalse(results[5].isLive)
        assertFalse(results[5].https)
    }

    @Test
    fun testExtraColumnsTolerated() {
        val csv = "url,name,extra1,extra2\nhttp://test1,Stream1,val1,val2"
        val results = parser.parse(csv)
        assertEquals(1, results.size)
        assertEquals("Stream1", results[0].name)
        assertEquals("http://test1", results[0].url)
    }
}
