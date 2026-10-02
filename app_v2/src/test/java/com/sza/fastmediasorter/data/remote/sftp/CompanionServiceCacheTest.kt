package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.domain.model.HostPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionServiceCacheTest {

    private val cache = CompanionServiceCache(ttlMs = TTL)
    private val endpoint = HostPort("192.168.1.40", 2022)

    @Test
    fun `fresh entry is served without a probe`() {
        cache.put(FP, endpoint, SERVICE, nowMs = 0)

        val hit = cache.lookup(FP, nowMs = TTL - 1)

        assertEquals(endpoint, hit?.endpoint)
        assertNull(hit?.probeServiceName)
    }

    @Test
    fun `expired entry is still served and starts exactly one probe`() {
        cache.put(FP, endpoint, SERVICE, nowMs = 0)

        val first = cache.lookup(FP, nowMs = TTL)
        val second = cache.lookup(FP, nowMs = TTL + 1)

        assertEquals(endpoint, first?.endpoint)
        assertEquals(SERVICE, first?.probeServiceName)
        assertEquals(endpoint, second?.endpoint)
        assertNull(second?.probeServiceName)
    }

    @Test
    fun `lost service is probed once and dropped when the probe fails`() {
        cache.put(FP, endpoint, SERVICE, nowMs = 0)

        assertTrue(cache.requestProbeForService(SERVICE))
        assertFalse(cache.requestProbeForService(SERVICE))
        cache.probeFailed(SERVICE)

        assertNull(cache.lookup(FP, nowMs = 1))
    }

    @Test
    fun `successful probe refreshes the entry and clears the pending flag`() {
        cache.put(FP, endpoint, SERVICE, nowMs = 0)
        cache.requestProbeForFingerprint(FP)
        val moved = HostPort("192.168.1.41", 2022)

        cache.put(FP, moved, SERVICE, nowMs = TTL)
        cache.probeFailed(SERVICE)

        assertEquals(moved, cache.lookup(FP, nowMs = TTL + 1)?.endpoint)
        assertEquals(SERVICE, cache.requestProbeForFingerprint(FP))
    }

    @Test
    fun `failed resolve of a service not under probe removes nothing`() {
        cache.put(FP, endpoint, SERVICE, nowMs = 0)

        cache.probeFailed(SERVICE)

        assertEquals(endpoint, cache.lookup(FP, nowMs = 1)?.endpoint)
    }

    @Test
    fun `unknown fingerprint and unknown service start no probe`() {
        assertNull(cache.lookup(FP, nowMs = 0))
        assertNull(cache.requestProbeForFingerprint(FP))
        assertFalse(cache.requestProbeForService(SERVICE))
    }

    private companion object {
        const val TTL = 120_000L
        const val FP = "SHA256:abc"
        const val SERVICE = "User-PC (FastMediaSorter)"
    }
}
