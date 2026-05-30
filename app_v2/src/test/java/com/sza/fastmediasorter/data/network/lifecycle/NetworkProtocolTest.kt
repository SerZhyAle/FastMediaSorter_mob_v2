package com.sza.fastmediasorter.data.network.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [NetworkProtocol.fromUri] scheme mapping. */
class NetworkProtocolTest {

    @Test
    fun `smb scheme maps to SMB`() {
        assertEquals(NetworkProtocol.SMB, NetworkProtocol.fromUri("smb://host/share"))
    }

    @Test
    fun `sftp scheme maps to SFTP`() {
        assertEquals(NetworkProtocol.SFTP, NetworkProtocol.fromUri("sftp://host/path"))
    }

    @Test
    fun `ftp scheme maps to FTP`() {
        assertEquals(NetworkProtocol.FTP, NetworkProtocol.fromUri("ftp://host/path"))
    }

    @Test
    fun `cloud scheme maps to CLOUD`() {
        assertEquals(NetworkProtocol.CLOUD, NetworkProtocol.fromUri("cloud://gdrive/root"))
    }

    @Test
    fun `scheme match is case insensitive`() {
        assertEquals(NetworkProtocol.SMB, NetworkProtocol.fromUri("SMB://HOST/SHARE"))
    }

    @Test
    fun `local path returns null`() {
        assertNull(NetworkProtocol.fromUri("/storage/emulated/0/file.mp4"))
    }

    @Test
    fun `unknown scheme returns null`() {
        assertNull(NetworkProtocol.fromUri("http://example.com"))
    }
}
