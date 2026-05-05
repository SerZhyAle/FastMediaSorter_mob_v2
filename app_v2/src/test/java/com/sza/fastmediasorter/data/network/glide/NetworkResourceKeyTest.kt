package com.sza.fastmediasorter.data.network.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkResourceKeyTest {

    @Test
    fun smbDefaultPort() {
        assertEquals(
            "smb://192.168.1.10:445",
            extractNetworkResourceKey("smb://192.168.1.10/share/file.mp4")
        )
    }

    @Test
    fun smbExplicitPort() {
        assertEquals(
            "smb://server:139",
            extractNetworkResourceKey("smb://server:139/share/file.mp4")
        )
    }

    @Test
    fun sftpDefaultPort() {
        assertEquals(
            "sftp://host:22",
            extractNetworkResourceKey("sftp://host/path/file.mp4")
        )
    }

    @Test
    fun sftpExplicitPort() {
        assertEquals(
            "sftp://host:2222",
            extractNetworkResourceKey("sftp://host:2222/path/file.mp4")
        )
    }

    @Test
    fun ftpDefaultPort() {
        assertEquals(
            "ftp://example.com:21",
            extractNetworkResourceKey("ftp://example.com/dir/file.mp4")
        )
    }

    @Test
    fun ftpExplicitPort() {
        assertEquals(
            "ftp://example.com:2121",
            extractNetworkResourceKey("ftp://example.com:2121/dir/file.mp4")
        )
    }

    @Test
    fun localPathReturnsNull() {
        assertNull(extractNetworkResourceKey("/storage/emulated/0/x.mp4"))
    }

    @Test
    fun emptyStringReturnsNull() {
        assertNull(extractNetworkResourceKey(""))
    }

    @Test
    fun cloudSchemeReturnsNull() {
        assertNull(extractNetworkResourceKey("cloud://drive/file.mp4"))
    }

    @Test
    fun pathBelongsToResourceMatch() {
        assertTrue(
            pathBelongsToResource(
                "smb://192.168.1.10/share/x.mp4",
                "smb://192.168.1.10:445"
            )
        )
    }

    @Test
    fun pathBelongsToResourceMismatch() {
        assertFalse(
            pathBelongsToResource(
                "smb://192.168.1.11/share/x.mp4",
                "smb://192.168.1.10:445"
            )
        )
    }

    @Test
    fun pathBelongsToResourceLocalIsFalse() {
        assertFalse(
            pathBelongsToResource(
                "/local/x.mp4",
                "smb://192.168.1.10:445"
            )
        )
    }
}
