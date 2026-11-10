package com.sza.fastmediasorter.core.network

import android.content.Context
import android.net.ConnectivityManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkContextAnalyzerTest {

    private val context = mockk<Context> {
        every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockk<ConnectivityManager>()
    }
    private val analyzer = NetworkContextAnalyzer(context)

    @Test
    fun `isLocalNetworkHost accepts RFC 1918 addresses`() {
        assertTrue(analyzer.isLocalNetworkHost("192.168.1.112"))
        assertTrue(analyzer.isLocalNetworkHost("10.0.0.5"))
        assertTrue(analyzer.isLocalNetworkHost("172.20.3.4"))
    }

    @Test
    fun `isLocalNetworkHost accepts IPv4 link-local addresses`() {
        assertTrue(analyzer.isLocalNetworkHost("169.254.10.20"))
    }

    @Test
    fun `isLocalNetworkHost accepts mDNS names`() {
        assertTrue(analyzer.isLocalNetworkHost("nas.local"))
        assertTrue(analyzer.isLocalNetworkHost("  NAS.Local "))
    }

    @Test
    fun `isLocalNetworkHost rejects a public address`() {
        assertFalse(analyzer.isLocalNetworkHost("8.8.8.8"))
    }

    @Test
    fun `isLocalNetworkHost rejects a DNS name`() {
        assertFalse(analyzer.isLocalNetworkHost("files.example.com"))
    }

    @Test
    fun `isLocalNetworkHost rejects an IPv6 literal`() {
        assertFalse(analyzer.isLocalNetworkHost("fe80::1"))
    }

    @Test
    fun `isLocalNetworkHost rejects loopback`() {
        assertFalse(analyzer.isLocalNetworkHost("127.0.0.1"))
    }
}
