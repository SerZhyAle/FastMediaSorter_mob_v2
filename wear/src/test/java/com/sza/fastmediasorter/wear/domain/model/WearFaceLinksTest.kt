package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearFaceLinksTest {

    @Test
    fun `face is not offered below Wear OS 6`() {
        assertFalse(WearFaceLinks.isAvailableOn(35))
    }

    @Test
    fun `face is offered from Wear OS 6`() {
        assertTrue(WearFaceLinks.isAvailableOn(36))
    }

    @Test
    fun `market address names the face package`() {
        assertEquals("market://details?id=com.sza.fastmediasorter.watchface", WearFaceLinks.MARKET_URL)
    }
}
