package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPrefetchManagerTest {

    @Test
    fun `SFTP audio early fallback uses short startup budget`() {
        val policy = PrefetchPolicyManager.audioStartupPolicyFor(
            path = "sftp://home.example.com:22/Music/track.mp3",
            isAudio = true
        )

        assertEquals(AudioPreCacheSourceType.SFTP, policy.sourceType)
        assertEquals(PrefetchPolicyManager.SFTP_AUDIO_STARTUP_PRECACHE_TIMEOUT_MS, policy.timeoutMs)
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
        assertEquals(PrefetchPolicyManager.DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS, policy.timeoutMs)
        assertFalse(policy.directStreamFallback)
        assertEquals("network-audio-default", policy.reason)
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
