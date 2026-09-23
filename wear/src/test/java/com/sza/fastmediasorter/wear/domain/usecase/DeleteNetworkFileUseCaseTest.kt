package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.network.ftp.FtpDataSource
import com.sza.fastmediasorter.wear.data.network.sftp.SftpDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the protocol routing of the destructive half of the move.
 *
 * The same reason S1687 gave for the download: a call that picks its own protocol picks the wrong one
 * eventually, and here the wrong one either destroys nothing or destroys a file on another server. The
 * path each layer is handed is asserted too, because SMB is addressed share-relative while the other
 * two take a full URI - three sources, two addressing schemes, and no server in the loop to catch a
 * mix-up.
 */
class DeleteNetworkFileUseCaseTest {

    private val smb = mockk<SmbDataSource>()
    private val ftp = mockk<FtpDataSource>()
    private val sftp = mockk<SftpDataSource>()

    @Test
    fun `an smb file is removed over the connection the reader already holds`() = runTest {
        val source = source(NetworkSourceType.SMB)
        every { smb.isConnected() } returns true
        coEvery { smb.deleteFile(any()) } returns Result.success(Unit)

        val result = useCase(source)(SOURCE_ID, SMB_PATH)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { smb.deleteFile(SMB_PATH) }
        coVerify(exactly = 0) { smb.connect(any()) }
    }

    /** A player outliving the browse connection is the case the reader reconnects for, so this does too. */
    @Test
    fun `an smb file on a dropped connection is removed after reconnecting`() = runTest {
        val source = source(NetworkSourceType.SMB)
        every { smb.isConnected() } returns false
        coEvery { smb.connect(source) } returns Result.success(Unit)
        coEvery { smb.deleteFile(any()) } returns Result.success(Unit)

        val result = useCase(source)(SOURCE_ID, SMB_PATH)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { smb.connect(source) }
    }

    @Test
    fun `an smb connection that cannot be re-established removes nothing`() = runTest {
        val source = source(NetworkSourceType.SMB)
        every { smb.isConnected() } returns false
        coEvery { smb.connect(source) } returns Result.failure(IllegalStateException("offline"))

        val result = useCase(source)(SOURCE_ID, SMB_PATH)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { smb.deleteFile(any()) }
    }

    @Test
    fun `an ftp file is removed over ftp at its server relative path`() = runTest {
        val source = source(NetworkSourceType.FTP)
        coEvery { ftp.deleteFile(source, any()) } returns Result.success(Unit)

        val result = useCase(source)(SOURCE_ID, "ftp://host:21/music/track.mp3")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { ftp.deleteFile(source, "/music/track.mp3") }
        coVerify(exactly = 0) { smb.deleteFile(any()) }
    }

    @Test
    fun `an sftp file is removed over sftp at its server relative path`() = runTest {
        val source = source(NetworkSourceType.SFTP)
        coEvery { sftp.deleteFile(source, any()) } returns Result.success(Unit)

        val result = useCase(source)(SOURCE_ID, "sftp://host:22/music/track.mp3")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { sftp.deleteFile(source, "/music/track.mp3") }
        coVerify(exactly = 0) { ftp.deleteFile(any(), any()) }
    }

    /** A read-only account is the ordinary case, and the move above reads this as "the copy was kept". */
    @Test
    fun `a share that refuses the removal is reported as a failure`() = runTest {
        val source = source(NetworkSourceType.FTP)
        coEvery { ftp.deleteFile(source, any()) } returns Result.failure(IllegalStateException("read-only"))

        val result = useCase(source)(SOURCE_ID, "ftp://host:21/music/track.mp3")

        assertTrue(result.isFailure)
    }

    /** The id names the share the file lives on; without it there is nothing to address a removal to. */
    @Test
    fun `a missing source id removes nothing`() = runTest {
        val result = useCase(source(NetworkSourceType.FTP))(null, "ftp://host:21/music/track.mp3")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { ftp.deleteFile(any(), any()) }
    }

    /** A source the owner deleted between the listing and the tap leaves the file where it is. */
    @Test
    fun `a source that is no longer configured removes nothing`() = runTest {
        val result = useCase(configured = null)(SOURCE_ID, "ftp://host:21/music/track.mp3")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { ftp.deleteFile(any(), any()) }
    }

    private fun useCase(configured: NetworkSource?): DeleteNetworkFileUseCase {
        val repository = mockk<NetworkSourceRepository>()
        coEvery { repository.getSourceById(SOURCE_ID) } returns configured
        return DeleteNetworkFileUseCase(repository, smb, ftp, sftp)
    }

    private fun source(type: NetworkSourceType) = NetworkSource(
        id = SOURCE_ID,
        type = type,
        name = "shelf",
        server = "host",
        username = "user",
        password = "secret"
    )

    private companion object {
        const val SOURCE_ID = "source-1"

        /** SMB addresses are share-relative in this module, so no scheme is stripped from one. */
        const val SMB_PATH = "music/track.mp3"
    }
}
