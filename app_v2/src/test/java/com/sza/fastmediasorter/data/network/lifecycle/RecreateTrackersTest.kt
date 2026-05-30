package com.sza.fastmediasorter.data.network.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the per-protocol recreate trackers
 * ([SmbRecreateTracker], [CloudRecreateTracker], [FtpRecreateTracker], [SftpRecreateTracker]).
 */
class RecreateTrackersTest {

    // ── SmbRecreateTracker ───────────────────────────────────────────────────

    @Test
    fun `smb tracker returns null before any record`() {
        assertNull(SmbRecreateTracker().lastRecreateMs("smb://host:445"))
    }

    @Test
    fun `smb tracker records and returns a recent timestamp`() {
        val tracker = SmbRecreateTracker()
        val before = System.currentTimeMillis()
        tracker.recordRecreate("smb://host:445")
        val ts = tracker.lastRecreateMs("smb://host:445")
        assertTrue(ts != null && ts >= before)
    }

    @Test
    fun `smb tracker key builders match documented format`() {
        val tracker = SmbRecreateTracker()
        assertEquals("smb://nas:139", tracker.keyForServer("nas", 139))
        assertEquals("smb://nas:445/media", tracker.keyForShare("nas", 445, "media"))
    }

    @Test
    fun `smb tracker keeps keys independent`() {
        val tracker = SmbRecreateTracker()
        tracker.recordRecreate("smb://a:445")
        assertNull(tracker.lastRecreateMs("smb://b:445"))
    }

    // ── CloudRecreateTracker ─────────────────────────────────────────────────

    @Test
    fun `cloud tracker records and key builder formats provider`() {
        val tracker = CloudRecreateTracker()
        assertEquals("cloud://gdrive", tracker.keyForProvider("gdrive"))
        assertNull(tracker.lastRecreateMs("cloud://gdrive"))
        tracker.recordRecreate("cloud://gdrive")
        assertTrue(tracker.lastRecreateMs("cloud://gdrive") != null)
    }

    // ── FtpRecreateTracker ───────────────────────────────────────────────────

    @Test
    fun `ftp tracker records and returns timestamp`() {
        val tracker = FtpRecreateTracker()
        assertNull(tracker.lastRecreateMs("ftp://host:21"))
        tracker.recordRecreate("ftp://host:21")
        assertTrue(tracker.lastRecreateMs("ftp://host:21") != null)
    }

    // ── SftpRecreateTracker ──────────────────────────────────────────────────

    @Test
    fun `sftp tracker records and returns timestamp`() {
        val tracker = SftpRecreateTracker()
        assertNull(tracker.lastRecreateMs("sftp://host:22"))
        tracker.recordRecreate("sftp://host:22")
        assertTrue(tracker.lastRecreateMs("sftp://host:22") != null)
    }
}
