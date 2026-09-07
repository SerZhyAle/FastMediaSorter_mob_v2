package com.sza.fastmediasorter.wear.data.network

import com.sza.fastmediasorter.wear.data.network.ftp.FtpDataSource
import com.sza.fastmediasorter.wear.data.network.sftp.SftpDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import io.mockk.mockk
import org.apache.commons.net.ftp.FTPFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * S2694: the directory flag has to survive every protocol, or the walk works on some shares and not
 * others - a failure that looks random to the wearer rather than like a missing feature. Each source
 * maps its own raw listing, so the guard has to be per source; one shared assertion would pass while
 * two of the three still dropped the flag.
 */
class NetworkEntryMappingTest {

    private val smb = SmbDataSource(mockk(relaxed = true))
    private val ftp = FtpDataSource(mockk(relaxed = true))
    private val sftp = SftpDataSource(mockk(relaxed = true))

    // --- SMB ---

    @Test
    fun `smb keeps the directory flag both ways`() {
        val entries = smb.toNetworkEntries(
            path = "photos",
            entries = listOf(
                SmbDataSource.SmbEntry(name = "2024", size = 0, modifiedTime = 1L, isDirectory = true),
                SmbDataSource.SmbEntry(name = "a.jpg", size = 12L, modifiedTime = 2L, isDirectory = false)
            )
        )

        assertTrue(entries.first { it.name == "2024" }.isDirectory)
        assertFalse(entries.first { it.name == "a.jpg" }.isDirectory)
    }

    @Test
    fun `smb joins share-relative and treats the share root as empty`() {
        assertEquals("photos/2024", smb.toNetworkEntries("photos", listOf(smbDir("2024"))).single().path)
        assertEquals("2024", smb.toNetworkEntries("", listOf(smbDir("2024"))).single().path)
    }

    @Test
    fun `smb drops the self and parent rows`() {
        val names = smb.toNetworkEntries(
            path = "photos",
            entries = listOf(smbDir("."), smbDir(".."), smbDir("2024"))
        ).map { it.name }

        assertEquals(listOf("2024"), names)
    }

    @Test
    fun `smb does not guess a directory from the name`() {
        val entries = smb.toNetworkEntries(
            path = "x",
            entries = listOf(
                SmbDataSource.SmbEntry(name = "README", size = 5L, modifiedTime = 1L, isDirectory = false),
                SmbDataSource.SmbEntry(name = "v1.2", size = 0L, modifiedTime = 1L, isDirectory = true)
            )
        )

        assertFalse("an extension-less file is not a directory", entries.first { it.name == "README" }.isDirectory)
        assertTrue("a dotted directory is not a file", entries.first { it.name == "v1.2" }.isDirectory)
    }

    // --- FTP ---

    @Test
    fun `ftp keeps the directory flag both ways`() {
        val entries = ftp.toNetworkEntries(
            path = "/media",
            files = listOf(ftpFile("2024", FTPFile.DIRECTORY_TYPE), ftpFile("a.mp3", FTPFile.FILE_TYPE))
        )

        assertTrue(entries.first { it.name == "2024" }.isDirectory)
        assertFalse(entries.first { it.name == "a.mp3" }.isDirectory)
    }

    @Test
    fun `ftp joins absolute and keeps a single separator at the root`() {
        assertEquals("/media/2024", ftp.toNetworkEntries("/media", listOf(ftpDir())).single().path)
        assertEquals("/2024", ftp.toNetworkEntries("/", listOf(ftpDir())).single().path)
        assertEquals("/media/2024", ftp.toNetworkEntries("/media/", listOf(ftpDir())).single().path)
    }

    @Test
    fun `ftp drops the self and parent rows`() {
        val names = ftp.toNetworkEntries(
            path = "/media",
            files = listOf(ftpFile(".", FTPFile.DIRECTORY_TYPE), ftpFile("..", FTPFile.DIRECTORY_TYPE), ftpDir())
        ).map { it.name }

        assertEquals(listOf("2024"), names)
    }

    // --- SFTP ---

    @Test
    fun `sftp keeps the directory flag and converts seconds to millis`() {
        val dir = sftp.toNetworkEntry("/srv", "2024", isDirectory = true, sizeBytes = 0L, modifiedEpochSeconds = 7L)
        val file = sftp.toNetworkEntry("/srv", "a.mp4", isDirectory = false, sizeBytes = 9L, modifiedEpochSeconds = 7L)

        assertTrue(dir.isDirectory)
        assertFalse(file.isDirectory)
        assertEquals(7_000L, dir.dateModifiedEpochMillis)
    }

    @Test
    fun `sftp joins absolute and keeps a single separator at the root`() {
        assertEquals("/srv/2024", sftpPathOf("/srv"))
        assertEquals("/srv/2024", sftpPathOf("/srv/"))
        assertEquals("/2024", sftpPathOf("/"))
    }

    private fun sftpPathOf(path: String) = sftp
        .toNetworkEntry(path, "2024", isDirectory = true, sizeBytes = 0L, modifiedEpochSeconds = 0L)
        .path

    private fun smbDir(name: String) =
        SmbDataSource.SmbEntry(name = name, size = 0L, modifiedTime = 0L, isDirectory = true)

    private fun ftpDir() = ftpFile("2024", FTPFile.DIRECTORY_TYPE)

    private fun ftpFile(name: String, type: Int) = FTPFile().apply {
        this.name = name
        this.type = type
        this.size = 0L
        this.timestamp = Calendar.getInstance()
    }
}
