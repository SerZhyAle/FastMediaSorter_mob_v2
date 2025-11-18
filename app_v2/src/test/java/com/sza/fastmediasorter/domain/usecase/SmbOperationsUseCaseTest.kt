package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmbOperationsUseCaseTest {

    private val smbClient: SmbClient = mockk()
    private val sftpClient: SftpClient = mockk(relaxed = true)
    private val ftpClient: FtpClient = mockk(relaxed = true)
    private val credentialsDao: NetworkCredentialsDao = mockk()

    private lateinit var useCase: SmbOperationsUseCase

    @Before
    fun setUp() {
        useCase = SmbOperationsUseCase(
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            credentialsDao = credentialsDao,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `saveCredentials persists SMB entity`() = runBlocking {
        val captured = slot<NetworkCredentialsEntity>()
        coJustRun { credentialsDao.insert(capture(captured)) }

        val result = useCase.saveCredentials(
            server = "srv",
            shareName = "share",
            username = "user",
            password = "pwd"
        )

        assertTrue(result.isSuccess)
        val entity = captured.captured
        assertEquals("SMB", entity.type)
        assertEquals("srv", entity.server)
        assertEquals("share", entity.shareName)
    }

    @Test
    fun `getConnectionInfo fails when credentials missing`() = runBlocking {
        coEvery { credentialsDao.getCredentialsById("missing") } returns null

        val result = useCase.getConnectionInfo("missing")

        assertTrue(result.isFailure)
    }
}
