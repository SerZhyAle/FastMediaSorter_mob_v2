package com.sza.fastmediasorter.data.link.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `youtube and music youtube no longer resolve to known social entry`() {
        // S0281: YouTube was removed from KnownAuthResources because Google's OAuth
        // policy mandates Chrome Custom Tabs (per S0200 ADR-4) and CCT cannot return
        // cookies to the app. Routing of google-OAuth-only hosts is now centralized
        // in GoogleDomainMatcher; matchHost must return null for these hosts so they
        // fall through the unknown-host (silent) path in ReceiveShareActivity.
        assertNull(KnownAuthResources.matchHost("youtube.com"))
        assertNull(KnownAuthResources.matchHost("www.youtube.com"))
        assertNull(KnownAuthResources.matchHost("music.youtube.com"))
        assertFalse(KnownAuthResources.isPreviewSensitiveHost("youtube.com"))
    }
}