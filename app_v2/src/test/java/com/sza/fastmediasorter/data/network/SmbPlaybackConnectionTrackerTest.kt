package com.sza.fastmediasorter.data.network

import com.sza.fastmediasorter.data.network.model.ConnectionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [SmbPlaybackConnectionTracker] - state transitions + watchdog lockout logic. */
class SmbPlaybackConnectionTrackerTest {

    private val key = ConnectionKey(
        server = "nas", port = 445, shareName = "media", username = "u", domain = ""
    )
    private val keyB = key.copy(server = "other")

    // ── State transitions ────────────────────────────────────────────────────

    @Test
    fun `absent key reports absent state`() {
        assertEquals("absent", SmbPlaybackConnectionTracker().getStateName(key))
    }

    @Test
    fun `created then validated then invalidated transitions`() {
        val t = SmbPlaybackConnectionTracker()
        t.onConnectionCreated(key)
        assertEquals("FRESH", t.getStateName(key))
        t.onConnectionValidated(key)
        assertEquals("VALIDATED", t.getStateName(key))
        t.onConnectionInvalidated(key)
        assertEquals("absent", t.getStateName(key))
    }

    // ── Watchdog: same-file lockout ──────────────────────────────────────────

    @Test
    fun `no watchdog means no fail-fast`() {
        assertFalse(SmbPlaybackConnectionTracker().isRecentWatchdog(key, "smb://nas/media/a.mp4"))
    }

    @Test
    fun `recent watchdog on same file triggers fail-fast`() {
        val t = SmbPlaybackConnectionTracker()
        t.recordWatchdog(key, "smb://nas/media/a.mp4")
        assertTrue(t.isRecentWatchdog(key, "smb://nas/media/a.mp4"))
    }

    @Test
    fun `recent watchdog on a different file does not fail-fast below escalation`() {
        val t = SmbPlaybackConnectionTracker()
        t.recordWatchdog(key, "smb://nas/media/a.mp4")
        // Single hit, different file -> count stays 1 < SERVER_ESCALATION_THRESHOLD(2).
        assertFalse(t.isRecentWatchdog(key, "smb://nas/media/b.mp4"))
    }

    // ── Watchdog: server-wide escalation ─────────────────────────────────────

    @Test
    fun `two watchdogs on different files escalate to server-wide lockout`() {
        val t = SmbPlaybackConnectionTracker()
        t.recordWatchdog(key, "smb://nas/media/a.mp4") // count 1
        t.recordWatchdog(key, "smb://nas/media/b.mp4") // count 2 -> escalates
        // Now ANY file on this server fails fast.
        assertTrue(t.isRecentWatchdog(key, "smb://nas/media/c.mp4"))
    }

    @Test
    fun `same uri repeated does not increment escalation count`() {
        val t = SmbPlaybackConnectionTracker()
        t.recordWatchdog(key, "smb://nas/media/a.mp4")
        t.recordWatchdog(key, "smb://nas/media/a.mp4") // same uri -> count unchanged (1)
        assertFalse(t.isRecentWatchdog(key, "smb://nas/media/b.mp4"))
    }

    @Test
    fun `watchdog is scoped per server key`() {
        val t = SmbPlaybackConnectionTracker()
        t.recordWatchdog(key, "smb://nas/media/a.mp4")
        assertFalse(t.isRecentWatchdog(keyB, "smb://nas/media/a.mp4"))
    }

    // ── Clearing ─────────────────────────────────────────────────────────────

    @Test
    fun `clearWatchdog removes only the watchdog for that key`() {
        val t = SmbPlaybackConnectionTracker()
        t.recordWatchdog(key, "smb://nas/media/a.mp4")
        t.clearWatchdog(key)
        assertFalse(t.isRecentWatchdog(key, "smb://nas/media/a.mp4"))
    }

    @Test
    fun `clearAllWatchdogs unblocks all servers`() {
        val t = SmbPlaybackConnectionTracker()
        t.recordWatchdog(key, "smb://nas/media/a.mp4")
        t.recordWatchdog(keyB, "smb://other/media/x.mp4")
        t.clearAllWatchdogs()
        assertFalse(t.isRecentWatchdog(key, "smb://nas/media/a.mp4"))
        assertFalse(t.isRecentWatchdog(keyB, "smb://other/media/x.mp4"))
    }

    @Test
    fun `clearAll resets both states and watchdogs`() {
        val t = SmbPlaybackConnectionTracker()
        t.onConnectionValidated(key)
        t.recordWatchdog(key, "smb://nas/media/a.mp4")
        t.clearAll()
        assertEquals("absent", t.getStateName(key))
        assertFalse(t.isRecentWatchdog(key, "smb://nas/media/a.mp4"))
    }
}
