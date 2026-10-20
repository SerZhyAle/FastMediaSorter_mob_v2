package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPortalLinksTest {

    @Test
    fun `each published language gets its own watch page`() {
        assertTrue(WearPortalLinks.watchExchangeUrl("en").endsWith("/media-exchange-portal/watch-exchange.html"))
        assertTrue(WearPortalLinks.watchExchangeUrl("ru").endsWith("/media-exchange-portal/watch-exchange-ru.html"))
        assertTrue(WearPortalLinks.watchExchangeUrl("uk").endsWith("/media-exchange-portal/watch-exchange-uk.html"))
    }

    @Test
    fun `a language without a portal edition gets the English page`() {
        val english = WearPortalLinks.watchExchangeUrl("en")
        assertEquals(english, WearPortalLinks.watchExchangeUrl("de"))
        assertEquals(english, WearPortalLinks.watchExchangeUrl(""))
        assertNotEquals(english, WearPortalLinks.watchExchangeUrl("RU"))
    }
}
