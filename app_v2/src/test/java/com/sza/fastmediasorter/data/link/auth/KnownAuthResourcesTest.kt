package com.sza.fastmediasorter.data.link.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownAuthResourcesTest {

    @Test
    fun `matches canonical host and subdomain`() {
        assertEquals("instagram.com", KnownAuthResources.matchHost("instagram.com")?.host)
        assertEquals("instagram.com", KnownAuthResources.matchHost("www.instagram.com")?.host)
        assertEquals("threads.net", KnownAuthResources.matchHost("m.threads.net")?.host)
    }

    @Test
    fun `keeps threads domains distinct`() {
        assertEquals("threads.net", KnownAuthResources.matchHost("threads.net")?.host)
        assertEquals("threads.com", KnownAuthResources.matchHost("threads.com")?.host)
    }

    @Test
    fun `marks only preview sensitive hosts`() {
        assertTrue(KnownAuthResources.isPreviewSensitiveHost("x.com"))
        assertTrue(KnownAuthResources.isPreviewSensitiveHost("www.instagram.com"))
        assertFalse(KnownAuthResources.isPreviewSensitiveHost("pinterest.com"))
    }

    @Test
    fun `youtube and music youtube resolve to youtube entry`() {
        assertEquals("youtube.com", KnownAuthResources.matchHost("youtube.com")?.host)
        assertEquals("youtube.com", KnownAuthResources.matchHost("www.youtube.com")?.host)
        assertEquals("youtube.com", KnownAuthResources.matchHost("music.youtube.com")?.host)
        assertFalse(KnownAuthResources.isPreviewSensitiveHost("youtube.com"))
    }
}