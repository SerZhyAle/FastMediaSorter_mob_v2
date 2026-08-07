package com.sza.fastmediasorter.data.link.streaming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [StreamingCacheCleaner]: session id generation, session-dir layout/creation,
 * recursive cleanup, and the 20%-margin pre-flight free-space gate. Pure JVM with TemporaryFolder.
 */
class StreamingCacheCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val cleaner = StreamingCacheCleaner()

    @Test
    fun `newSessionId is 8 chars and varies`() {
        val a = cleaner.newSessionId()
        val b = cleaner.newSessionId()
        assertTrue(a.length == 8)
        assertNotEquals(a, b)
    }

    @Test
    fun `sessionDir creates url-stream sessionId path`() {
        val dir = cleaner.sessionDir(tempFolder.root, "abc12345")
        assertTrue(dir.isDirectory)
        assertTrue(dir.absolutePath.endsWith("url-stream${File.separator}abc12345"))
    }

    @Test
    fun `cleanupSession removes the session tree`() {
        val session = cleaner.sessionDir(tempFolder.root, "sess1")
        File(session, "seg0.ts").writeText("data")

        cleaner.cleanupSession(tempFolder.root, "sess1")

        assertFalse(session.exists())
    }

    @Test
    fun `cleanupSession is a no-op when session absent`() {
        // Should not throw.
        cleaner.cleanupSession(tempFolder.root, "missing")
    }

    @Test
    fun `preflightCheck passes when ample free space`() {
        // 1 KiB required against a real temp dir with plenty of usable space.
        assertTrue(cleaner.preflightCheck(tempFolder.root, requiredBytes = 1024L))
    }

    @Test
    fun `preflightCheck fails when required exceeds usable space`() {
        // Require more than any disk can supply.
        assertFalse(cleaner.preflightCheck(tempFolder.root, requiredBytes = Long.MAX_VALUE / 2))
    }
}
