package com.sza.fastmediasorter.data.transfer.strategies

import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the scheme-routing matrix of every cross-protocol [TransferStrategy.supports]
 * (the strategy-selection logic). Each strategy's collaborators are irrelevant to supports(), so
 * they are constructed with relaxed mocks. The Uri-bound copy() bodies are exercised elsewhere /
 * left to instrumentation - here we pin only the pure scheme predicate.
 *
 * Local schemes accepted by the "*ToLocal sink" strategies: "file", "content", null.
 */
class TransferStrategySupportsTest {

    private val ftpToFtp = FtpToFtpStrategy(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    private val ftpToLocal = FtpToLocalStrategy(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    private val localToFtp = LocalToFtpStrategy(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    private val localToSftp = LocalToSftpStrategy(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    private val localToSmb = LocalToSmbStrategy(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    private val sftpToLocal = SftpToLocalStrategy(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    private val sftpToSftp = SftpToSftpStrategy(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    private val smbToLocal = SmbToLocalStrategy(mockk(relaxed = true), mockk(relaxed = true))
    private val smbToSmb = SmbToSmbStrategy(mockk(relaxed = true))

    @Test
    fun `same-protocol strategies require both schemes equal`() {
        assertTrue(ftpToFtp.supports("ftp", "ftp"))
        assertFalse(ftpToFtp.supports("ftp", "file"))
        assertFalse(ftpToFtp.supports("smb", "ftp"))

        assertTrue(sftpToSftp.supports("sftp", "sftp"))
        assertFalse(sftpToSftp.supports("sftp", "ftp"))

        assertTrue(smbToSmb.supports("smb", "smb"))
        assertFalse(smbToSmb.supports("smb", "file"))
    }

    @Test
    fun `download strategies accept file content and null as local dest`() {
        for (localDest in listOf("file", "content", null)) {
            assertTrue("ftp->$localDest", ftpToLocal.supports("ftp", localDest))
            assertTrue("sftp->$localDest", sftpToLocal.supports("sftp", localDest))
        }
        assertFalse(ftpToLocal.supports("ftp", "smb"))
        assertFalse(sftpToLocal.supports("smb", "file"))
    }

    @Test
    fun `smbToLocal accepts only file or null local dest`() {
        assertTrue(smbToLocal.supports("smb", "file"))
        assertTrue(smbToLocal.supports("smb", null))
        // SmbToLocal does not list "content" as local
        assertFalse(smbToLocal.supports("smb", "content"))
        assertFalse(smbToLocal.supports("ftp", "file"))
    }

    @Test
    fun `upload strategies accept file content null source and matching dest scheme`() {
        for (localSrc in listOf("file", "content", null)) {
            assertTrue("$localSrc->ftp", localToFtp.supports(localSrc, "ftp"))
            assertTrue("$localSrc->sftp", localToSftp.supports(localSrc, "sftp"))
            assertTrue("$localSrc->smb", localToSmb.supports(localSrc, "smb"))
        }
        assertFalse(localToFtp.supports("smb", "ftp"))
        assertFalse(localToSftp.supports("file", "ftp"))
        assertFalse(localToSmb.supports("file", "sftp"))
    }
}
