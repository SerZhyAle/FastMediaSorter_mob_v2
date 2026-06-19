package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPrefetchManagerTest {

    @Test
    fun `SFTP audio uses short connect bound and generous transfer default`() {
        val policy = PrefetchPolicyManager.audioStartupPolicyFor(
            path = "sftp://home.example.com:22/Music/track.mp3",
            isAudio = true
        )

        assertEquals(AudioPreCacheSourceType.SFTP, policy.sourceType)
        assertEquals(PrefetchPolicyManager.NETWORK_AUDIO_CONNECT_TIMEOUT_MS, policy.connectTimeoutMs)
        // No measured speed supplied -> generous default transfer budget (server already answered connect).
        assertEquals(PrefetchPolicyManager.DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS, policy.transferTimeoutMs)
        assertTrue(policy.directStreamFallback)
        assertEquals("sftp-audio-early-direct-stream", policy.reason)
    }

    @Test
    fun `SFTP video keeps default pre-cache behavior`() {
        val policy = PrefetchPolicyManager.audioStartupPolicyFor(
            path = "sftp://home.example.com:22/Movies/movie.mkv",
            isAudio = false
        )

        assertEquals(AudioPreCacheSourceType.SFTP, policy.sourceType)
        assertEquals(PrefetchPolicyManager.NETWORK_AUDIO_CONNECT_TIMEOUT_MS, policy.connectTimeoutMs)
        assertEquals(PrefetchPolicyManager.DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS, policy.transferTimeoutMs)
        assertFalse(policy.directStreamFallback)
        assertEquals("network-audio-default", policy.reason)
    }

    @Test
    fun `transfer budget adapts to measured speed and clamps to floor`() {
        // A fast link on a small file estimates well under the floor -> clamped to the default floor.
        val fast = PrefetchPolicyManager.transferTimeoutMsFor(speedMbps = 50.0, fileSizeBytes = 4_000_000L)
        assertEquals(PrefetchPolicyManager.DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS, fast)

        // A slow link on a large file estimates above the floor and below the ceiling.
        val slow = PrefetchPolicyManager.transferTimeoutMsFor(speedMbps = 1.0, fileSizeBytes = 8_000_000L)
        assertTrue(slow > PrefetchPolicyManager.DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS)
        assertTrue(slow <= PrefetchPolicyManager.MAX_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS)

        // Unknown speed -> generous default.
        assertEquals(
            PrefetchPolicyManager.DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS,
            PrefetchPolicyManager.transferTimeoutMsFor(speedMbps = null, fileSizeBytes = 8_000_000L)
        )
    }

    @Test
    fun `next-track prefetch failure keeps current track recoverable`() {
        val recovery = PrefetchPolicyManager.nextTrackPrefetchRecovery(
            path = "sftp://home.example.com:22/Music/next.mp3"
        )

        assertEquals(AudioPreCacheSourceType.SFTP, recovery.sourceType)
        assertTrue(recovery.currentTrackUnaffected)
        assertTrue(recovery.retryOnDemand)
        assertEquals("sftp-next-audio-prefetch-degraded", recovery.reason)
    }
}
