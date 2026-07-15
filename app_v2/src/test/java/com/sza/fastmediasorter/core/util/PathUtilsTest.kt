package com.sza.fastmediasorter.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1028: regression net locking the union behavior of the consolidated network-path
 * matcher before the 5 duplicate sites route through it. Covers scheme-based
 * classification with/without cloud and the File-mangling-tolerant protocol match.
 */
class PathUtilsTest {

    @Test
    fun `isNetworkPath includeCloud true accepts all four schemes`() {
        assertTrue(PathUtils.isNetworkPath("smb://server/share/file.jpg"))
        assertTrue(PathUtils.isNetworkPath("sftp://host/dir/file.png"))
        assertTrue(PathUtils.isNetworkPath("ftp://host/dir/file.gif"))
        assertTrue(PathUtils.isNetworkPath("cloud://provider/file.mp4"))
    }

    @Test
    fun `isNetworkPath includeCloud false excludes cloud but keeps smb sftp ftp`() {
        assertTrue(PathUtils.isNetworkPath("smb://server/share/file.jpg", includeCloud = false))
        assertTrue(PathUtils.isNetworkPath("sftp://host/dir/file.png", includeCloud = false))
        assertTrue(PathUtils.isNetworkPath("ftp://host/dir/file.gif", includeCloud = false))
        assertFalse(PathUtils.isNetworkPath("cloud://provider/file.mp4", includeCloud = false))
    }

    @Test
    fun `isNetworkPath rejects local and content paths in both modes`() {
        assertFalse(PathUtils.isNetworkPath("/local/path/file.jpg"))
        assertFalse(PathUtils.isNetworkPath("/local/path/file.jpg", includeCloud = false))
        assertFalse(PathUtils.isNetworkPath("content://media/external/images/1"))
        assertFalse(PathUtils.isNetworkPath("content://media/external/images/1", includeCloud = false))
    }

    @Test
    fun `fileMatchesProtocol matches all four slash-mangled forms per protocol`() {
        for (protocol in listOf("smb", "sftp", "ftp", "cloud")) {
            assertTrue(PathUtils.fileMatchesProtocol("$protocol://host/file", protocol))
            assertTrue(PathUtils.fileMatchesProtocol("/$protocol://host/file", protocol))
            assertTrue(PathUtils.fileMatchesProtocol("/$protocol:/host/file", protocol))
            assertTrue(PathUtils.fileMatchesProtocol("$protocol:/host/file", protocol))
        }
    }

    @Test
    fun `fileMatchesProtocol rejects non-matching protocol and local path`() {
        assertFalse(PathUtils.fileMatchesProtocol("smb://host/file", "ftp"))
        assertFalse(PathUtils.fileMatchesProtocol("/local/path/file.jpg", "smb"))
    }
}
