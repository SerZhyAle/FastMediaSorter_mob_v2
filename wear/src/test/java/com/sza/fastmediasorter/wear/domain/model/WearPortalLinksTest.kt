package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPortalLinksTest {

    @Test
    fun `each published language gets its own start page`() {
        assertTrue(WearPortalLinks.portalUrl("en").endsWith("/media-exchange-portal/"))
        assertTrue(WearPortalLinks.portalUrl("ru").endsWith("/media-exchange-portal/index-ru.html"))
        assertTrue(WearPortalLinks.portalUrl("uk").endsWith("/media-exchange-portal/index-uk.html"))
    }

    @Test
    fun `a language without a portal edition gets the English page`() {
        val english = WearPortalLinks.portalUrl("en")
        assertEquals(english, WearPortalLinks.portalUrl("de"))
        assertEquals(english, WearPortalLinks.portalUrl(""))
        assertNotEquals(english, WearPortalLinks.portalUrl("RU"))
    }
}
