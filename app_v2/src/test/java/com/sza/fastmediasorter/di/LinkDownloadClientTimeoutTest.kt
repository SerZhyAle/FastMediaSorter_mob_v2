package com.sza.fastmediasorter.di

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1139: the shared link-download OkHttp client must NOT impose a total-call ceiling (callTimeout).
 * A fixed callTimeout cancels a legitimately long media download mid-transfer (HTTP/2 CANCEL ->
 * InterruptedIOException: timeout), which is exactly what threads.com downloads hit. Idle read/connect
 * timeouts still bound a truly stalled connection. These tests lock the invariant so a future edit
 * cannot silently re-introduce a callTimeout that caps arbitrary-size downloads.
 */
class LinkDownloadClientTimeoutTest {

    @Test
    fun `link download client has no total call timeout ceiling`() {
        val client = LinkDownloadModule.provideLinkDownloadClient(mockk(relaxed = true))
        assertEquals("callTimeout must be disabled for arbitrary-size media downloads", 0, client.callTimeoutMillis)
    }

    @Test
    fun `link download client keeps idle read and connect timeouts`() {
        val client = LinkDownloadModule.provideLinkDownloadClient(mockk(relaxed = true))
        assertEquals("readTimeout guards a stalled stream", 30_000, client.readTimeoutMillis)
        assertEquals("connectTimeout guards a dead host", 15_000, client.connectTimeoutMillis)
    }
}
