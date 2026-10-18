package com.sza.fastmediasorter.wear.domain.openin

import org.junit.Assert.assertEquals
import org.junit.Test

/** S4381: which intent a clipboard text deserves - an address, a link, a place or plain words. */
class WearOpenInTextClassifierTest {

    private fun shapeOf(text: String) = WearOpenInTextClassifier.classify(text)

    @Test
    fun `https and http addresses with a host are web addresses`() {
        assertEquals(WearOpenInTextShape.WEB_ADDRESS, shapeOf("https://example.com/a?b=c"))
        assertEquals(WearOpenInTextShape.WEB_ADDRESS, shapeOf("  HTTP://Example.com  "))
        assertEquals(WearOpenInTextShape.WEB_ADDRESS, shapeOf("https://user@host:8080/x"))
    }

    @Test
    fun `an address without a host is plain text`() {
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("https://"))
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("https:///path"))
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("https:example.com"))
    }

    @Test
    fun `whitespace inside makes the text plain even when it starts like an address`() {
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("https://example.com is the site"))
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("Note: buy milk"))
    }

    @Test
    fun `mail phone and message links are links`() {
        assertEquals(WearOpenInTextShape.LINK, shapeOf("mailto:a@b.c"))
        assertEquals(WearOpenInTextShape.LINK, shapeOf("tel:+441234"))
        assertEquals(WearOpenInTextShape.LINK, shapeOf("sms:+441234"))
        assertEquals(WearOpenInTextShape.LINK, shapeOf("smsto:+441234"))
    }

    @Test
    fun `an empty link scheme is plain text`() {
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("mailto:"))
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("geo:"))
    }

    @Test
    fun `a geo uri and a coordinate pair in range are a place`() {
        assertEquals(WearOpenInTextShape.GEO_POINT, shapeOf("geo:51.5,-0.12"))
        assertEquals(WearOpenInTextShape.GEO_POINT, shapeOf("51.5074, -0.1278"))
        assertEquals(WearOpenInTextShape.GEO_POINT, shapeOf("-33.86,151.21"))
    }

    @Test
    fun `a coordinate pair out of range is plain text`() {
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("91.0,10.0"))
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("10.0,181.0"))
    }

    @Test
    fun `free text and an unknown scheme are plain`() {
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("see you at eight"))
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("ftp://example.com"))
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("example.com"))
    }

    @Test
    fun `empty and blank text is plain`() {
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf(""))
        assertEquals(WearOpenInTextShape.PLAIN, shapeOf("   "))
    }
}
