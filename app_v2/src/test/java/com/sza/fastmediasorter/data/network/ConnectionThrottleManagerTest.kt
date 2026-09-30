package com.sza.fastmediasorter.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for the pure / cache-backed decision helpers of the [ConnectionThrottleManager]
 * singleton. Each test uses a unique resource key (UUID) so shared singleton state cannot leak
 * between tests. The throttling/semaphore execution paths and the video-player resume timer are
 * not asserted (they own a real Default-dispatcher coroutine scope + delay).
 */
class ConnectionThrottleManagerTest {

    private fun key() = "smb://${UUID.randomUUID()}:445"

    // ── getSmbjClientTier ────────────────────────────────────────────────────

    @Test
    fun `tier is SLOW when no speed measurement exists`() {
        assertEquals(ConnectionThrottleManager.SmbjClientTier.SLOW, ConnectionThrottleManager.getSmbjClientTier(key()))
    }

    @Test
    fun `tier follows the recorded speed`() {
        val fast = key()
        val medium = key()
        val slow = key()
        ConnectionThrottleManager.setLastSpeedMbps(fast, 150.0)
        ConnectionThrottleManager.setLastSpeedMbps(medium, 50.0)
        ConnectionThrottleManager.setLastSpeedMbps(slow, 5.0)
        assertEquals(ConnectionThrottleManager.SmbjClientTier.FAST, ConnectionThrottleManager.getSmbjClientTier(fast))
        assertEquals(
            ConnectionThrottleManager.SmbjClientTier.MEDIUM,
            ConnectionThrottleManager.getSmbjClientTier(medium)
        )
        assertEquals(ConnectionThrottleManager.SmbjClientTier.SLOW, ConnectionThrottleManager.getSmbjClientTier(slow))
        assertEquals(50.0, ConnectionThrottleManager.getLastSpeedMbps(medium) ?: 0.0, 0.0)
    }

    @Test
    fun `last speed is null before any measurement`() {
        assertNull(ConnectionThrottleManager.getLastSpeedMbps(key()))
    }

    // ── recommended threads / buffer ─────────────────────────────────────────

    @Test
    fun `recommended threads round-trip`() {
        val k = key()
        assertNull(ConnectionThrottleManager.getRecommendedThreads(k))
        ConnectionThrottleManager.setRecommendedThreads(k, 6)
        assertEquals(6, ConnectionThrottleManager.getRecommendedThreads(k))
    }

    @Test
    fun `recommended buffer returns default when unset`() {
        assertEquals(64 * 1024, ConnectionThrottleManager.getRecommendedBufferSize(key()))
    }

    @Test
    fun `recommended buffer round-trips`() {
        val k = key()
        ConnectionThrottleManager.setRecommendedBufferSize(k, 256 * 1024)
        assertEquals(256 * 1024, ConnectionThrottleManager.getRecommendedBufferSize(k))
    }

    // ── user network limit ───────────────────────────────────────────────────

    @Test
    fun `user network limit setter and getter agree`() {
        ConnectionThrottleManager.setUserNetworkLimit(5)
        assertEquals(5, ConnectionThrottleManager.getUserNetworkLimit())
    }

    // ── video player mode flag ────────────────────────────────────────────────

    @Test
    fun `activate video player marks resource active`() {
        val k = key()
        ConnectionThrottleManager.activateVideoPlayerMode(k)
        assertTrue(ConnectionThrottleManager.isVideoPlayerActive())
        assertTrue(ConnectionThrottleManager.isVideoPlayerActiveForResource(k))
        // Clean up shared singleton state so the global flag does not leak to other suites.
        ConnectionThrottleManager.deactivateVideoPlayerMode(k)
    }

    @Test
    fun `resource is not active before activation`() {
        assertFalse(ConnectionThrottleManager.isVideoPlayerActiveForResource(key()))
    }
}
