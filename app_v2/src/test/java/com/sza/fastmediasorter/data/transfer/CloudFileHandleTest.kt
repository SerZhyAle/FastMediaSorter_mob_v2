package com.sza.fastmediasorter.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [CloudFileHandle]: the overrides must surface the cloud display-name, the full
 * cloud:// path (not the File basename), and the cached size - the whole point of the wrapper.
 */
class CloudFileHandleTest {

    @Test
    fun `getName returns display name not path basename`() {
        val handle = CloudFileHandle(
            cloudPath = "cloud://gdrive/folderId/abc123",
            displayName = "Vacation.jpg",
            size = 2048L
        )
        assertEquals("Vacation.jpg", handle.name)
    }

    @Test
    fun `path and absolutePath return the cloud path verbatim`() {
        val handle = CloudFileHandle("cloud://dropbox/x/y.mp4", "y.mp4", 10L)
        assertEquals("cloud://dropbox/x/y.mp4", handle.path)
        assertEquals("cloud://dropbox/x/y.mp4", handle.absolutePath)
    }

    @Test
    fun `length returns cached size`() {
        assertEquals(4096L, CloudFileHandle("cloud://x", "n", 4096L).length())
    }

    @Test
    fun `length defaults to zero`() {
        assertEquals(0L, CloudFileHandle("cloud://x", "n").length())
    }
}
