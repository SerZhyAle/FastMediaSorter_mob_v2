package com.sza.fastmediasorter.wear.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1848: a source name is concatenated into a route string that the navigation graph parses back as a
 * URI. Every case here fails on the pre-S1848 builders, which substituted the raw value.
 */
class WearRoutesTest {

    @Test
    fun `an ampersand in the name does not open a second query pair`() {
        val route = WearRoutes.sourceMediaType("id-1", "Home & NAS")

        assertEquals(
            "source_media_type?sourceId=id-1&sourceName=Home%20%26%20NAS",
            route,
        )
        // One separator only: the one this builder wrote between sourceId and sourceName.
        assertEquals(1, route.count { it == '&' })
    }

    @Test
    fun `a slash in the name cannot invent a path segment`() {
        val route = WearRoutes.browseSource("AUDIO", "id-2", "Music/Rock")

        assertTrue(route.startsWith("browse/AUDIO?"))
        assertTrue(route.endsWith("sourceName=Music%2FRock"))
        // "browse/AUDIO" is the only path part; the name contributes no further '/'.
        assertEquals(1, route.substringBefore('?').count { it == '/' })
    }

    @Test
    fun `a question mark in the name does not start a second query`() {
        val route = WearRoutes.sourceMediaType("id-3", "What?")

        assertEquals(1, route.count { it == '?' })
        assertTrue(route.endsWith("sourceName=What%3F"))
    }

    @Test
    fun `a space becomes a percent escape and never a plus`() {
        val encoded = WearRoutes.encodeArg("Home NAS")

        assertEquals("Home%20NAS", encoded)
        // URLEncoder would emit '+' here, which URI decoding does not turn back into a space.
        assertTrue(!encoded.contains('+'))
    }

    @Test
    fun `unreserved characters are left alone`() {
        assertEquals("abcXYZ-019_.~", WearRoutes.encodeArg("abcXYZ-019_.~"))
    }

    @Test
    fun `non-ascii is encoded as utf-8 bytes`() {
        // Cyrillic "Дом" - two bytes per character, so six escapes.
        val encoded = WearRoutes.encodeArg("Дом")

        assertEquals("%D0%94%D0%BE%D0%BC", encoded)
    }

    @Test
    fun `the id is encoded too, not only the name`() {
        val route = WearRoutes.sourceMediaType("a&b", "plain")

        assertTrue(route.contains("sourceId=a%26b"))
    }
}
