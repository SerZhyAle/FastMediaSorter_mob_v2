package com.sza.fastmediasorter.data.link.cookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S0176: Contract tests for [registrableDomainOrNull] — freezes the PSL-aware
 * registrable-domain parsing behaviour expected by the link-download cookie path.
 */
class LinkCookieDomainResolverTest {

    @Test
    fun base_domain_returns_registrable_domain() {
        assertEquals("instagram.com", registrableDomainOrNull("instagram.com"))
    }

    @Test
    fun www_subdomain_strips_to_registrable_domain() {
        assertEquals("instagram.com", registrableDomainOrNull("www.instagram.com"))
    }

    @Test
    fun deep_cdn_subdomain_resolves_to_registrable_domain() {
        assertEquals("tiktok.com", registrableDomainOrNull("v16-webapp-prime.tiktok.com"))
    }

    @Test
    fun ip_address_returns_null() {
        assertNull(registrableDomainOrNull("192.168.1.1"))
    }

    @Test
    fun localhost_returns_null() {
        assertNull(registrableDomainOrNull("localhost"))
    }

    @Test
    fun blank_host_returns_null() {
        assertNull(registrableDomainOrNull(""))
    }

    @Test
    fun bare_public_suffix_returns_null() {
        // "com" alone has no registrable domain — topPrivateDomain() returns null.
        assertNull(registrableDomainOrNull("com"))
    }
}
