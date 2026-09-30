package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchFaceLinksTest {

    @Test
    fun `market address names the face package`() {
        assertEquals("market://details?id=com.sza.fastmediasorter.watchface", WatchFaceLinks.MARKET_URL)
    }

    @Test
    fun `web address names the face package`() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.sza.fastmediasorter.watchface",
            WatchFaceLinks.WEB_URL,
        )
    }

    @Test
    fun `both addresses end with the same package id`() {
        assertEquals(true, WatchFaceLinks.MARKET_URL.endsWith("=${WatchFaceLinks.PACKAGE_ID}"))
        assertEquals(true, WatchFaceLinks.WEB_URL.endsWith("=${WatchFaceLinks.PACKAGE_ID}"))
    }
}
