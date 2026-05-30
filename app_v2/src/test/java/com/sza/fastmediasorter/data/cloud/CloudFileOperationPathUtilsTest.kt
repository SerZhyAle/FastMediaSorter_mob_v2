package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CloudFileOperationPathUtils]: scheme normalization, [ResourceType] classification,
 * network-path detection, SFTP/FTP remote-path extraction, and MIME-type guessing. Pure JVM.
 */
class CloudFileOperationPathUtilsTest {

    private val utils = CloudFileOperationPathUtils(CloudPathParser())

    private fun creds(server: String, port: Int) =
        NetworkCredentialsResolver.NetworkCredentials(
            server = server,
            port = port,
            username = "u",
            password = "p"
        )

    @Test
    fun `normalizeNetworkPath strips leading slash`() {
        assertEquals("local/path", utils.normalizeNetworkPath("/local/path"))
    }

    @Test
    fun `normalizeNetworkPath upgrades single-slash schemes`() {
        assertEquals("smb://srv/share", utils.normalizeNetworkPath("smb:/srv/share"))
        assertEquals("sftp://srv/p", utils.normalizeNetworkPath("sftp:/srv/p"))
        assertEquals("ftp://srv/p", utils.normalizeNetworkPath("ftp:/srv/p"))
        assertEquals("cloud://x/y", utils.normalizeNetworkPath("cloud:/x/y"))
    }

    @Test
    fun `normalizeNetworkPath leaves double-slash schemes untouched`() {
        assertEquals("smb://srv/share", utils.normalizeNetworkPath("smb://srv/share"))
    }

    @Test
    fun `getResourceType classifies each scheme`() {
        assertEquals(ResourceType.CLOUD, utils.getResourceType("cloud://gdrive/x"))
        assertEquals(ResourceType.SMB, utils.getResourceType("smb://srv/share"))
        assertEquals(ResourceType.SFTP, utils.getResourceType("sftp://srv/p"))
        assertEquals(ResourceType.FTP, utils.getResourceType("ftp://srv/p"))
        assertEquals(ResourceType.LOCAL, utils.getResourceType("/storage/file.jpg"))
    }

    @Test
    fun `getResourceType handles single-slash scheme via normalization`() {
        assertEquals(ResourceType.SMB, utils.getResourceType("smb:/srv/share"))
    }

    @Test
    fun `isNetworkPath true only for smb sftp ftp`() {
        assertTrue(utils.isNetworkPath("smb://srv/share"))
        assertTrue(utils.isNetworkPath("sftp://srv/p"))
        assertTrue(utils.isNetworkPath("ftp://srv/p"))
        assertFalse(utils.isNetworkPath("cloud://gdrive/x"))
        assertFalse(utils.isNetworkPath("/local/file"))
    }

    @Test
    fun `extractSftpRemotePath strips host with port`() {
        val path = "sftp://10.0.0.5:2222/home/user/file.jpg"
        assertEquals("/home/user/file.jpg", utils.extractSftpRemotePath(path, creds("10.0.0.5", 2222)))
    }

    @Test
    fun `extractSftpRemotePath strips host without port`() {
        val path = "sftp://10.0.0.5/home/user/file.jpg"
        // creds port differs so withPortPrefix won't match, withoutPortPrefix matches
        assertEquals("/home/user/file.jpg", utils.extractSftpRemotePath(path, creds("10.0.0.5", 22)))
    }

    @Test
    fun `extractSftpRemotePath fallback strips bare scheme`() {
        val path = "sftp://otherhost/p/file.jpg"
        assertEquals("otherhost/p/file.jpg", utils.extractSftpRemotePath(path, creds("10.0.0.5", 22)))
    }

    @Test
    fun `extractFtpRemotePath strips host with port`() {
        val path = "ftp://ftp.example.com:2121/pub/file.bin"
        assertEquals("/pub/file.bin", utils.extractFtpRemotePath(path, creds("ftp.example.com", 2121)))
    }

    @Test
    fun `getMimeType maps known extensions`() {
        assertEquals("image/jpeg", CloudFileOperationPathUtils.getMimeType("a.JPG"))
        assertEquals("image/jpeg", CloudFileOperationPathUtils.getMimeType("a.jpeg"))
        assertEquals("image/png", CloudFileOperationPathUtils.getMimeType("a.png"))
        assertEquals("image/gif", CloudFileOperationPathUtils.getMimeType("a.gif"))
        assertEquals("image/webp", CloudFileOperationPathUtils.getMimeType("a.webp"))
        assertEquals("video/mp4", CloudFileOperationPathUtils.getMimeType("a.mp4"))
        assertEquals("video/webm", CloudFileOperationPathUtils.getMimeType("a.webm"))
        assertEquals("video/x-matroska", CloudFileOperationPathUtils.getMimeType("a.mkv"))
        assertEquals("audio/mpeg", CloudFileOperationPathUtils.getMimeType("a.mp3"))
        assertEquals("audio/wav", CloudFileOperationPathUtils.getMimeType("a.wav"))
    }

    @Test
    fun `getMimeType falls back to octet-stream`() {
        assertEquals("application/octet-stream", CloudFileOperationPathUtils.getMimeType("file.xyz"))
        assertEquals("application/octet-stream", CloudFileOperationPathUtils.getMimeType("noext"))
    }
}
