package com.sza.fastmediasorter.data.network

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.io.IOException

/**
 * S1138: a close() I/O error on the local source (e.g. EIO on a FUSE-backed /storage path that an
 * external producer deleted mid-read) must not override an already-successful SMB upload. These tests
 * lock the invariant that [closeLocalSourceQuietly] swallows a close IOException instead of raising it.
 */
class SmbFileOperationHandlerCloseTest {

    @Test
    fun `close IOException is swallowed, not propagated`() {
        var attempted = false
        val throwing = Closeable {
            attempted = true
            throw IOException("close failed: EIO (I/O error)")
        }

        // Must return normally - throwing here would turn a completed upload into a spurious FAILURE.
        closeLocalSourceQuietly(throwing)

        assertTrue("close() should have been attempted", attempted)
    }

    @Test
    fun `normal stream is closed`() {
        var closed = false
        val normal = Closeable { closed = true }

        closeLocalSourceQuietly(normal)

        assertTrue("stream should be closed", closed)
    }
}
