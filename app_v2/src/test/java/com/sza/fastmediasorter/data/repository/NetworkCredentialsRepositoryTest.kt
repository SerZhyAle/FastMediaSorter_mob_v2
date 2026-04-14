package com.sza.fastmediasorter.data.repository

import android.content.Context
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.model.AppSettings
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File

@ExperimentalCoroutinesApi
class NetworkCredentialsRepositoryTest {

    private lateinit var repository: NetworkCredentialsRepositoryImpl
    private lateinit var mockContext: Context
    private lateinit var mockDao: NetworkCredentialsDao
    private lateinit var mockResourceDao: ResourceDao
    private lateinit var mockSettingsRepository: SettingsRepository

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockDao = mockk(relaxed = true)
        mockResourceDao = mockk(relaxed = true)
        mockSettingsRepository = mockk(relaxed = true)

        // Mock external files dir to prevent actual file I/O in tests
        val mockExternalFilesDir = mockk<File>(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns mockExternalFilesDir
        every { mockExternalFilesDir.absolutePath } returns "/mock/path"
        
        // Mock settings flow
        val mockSettings = mockk<AppSettings>(relaxed = true)
        every { mockSettings.defaultUser } returns ""
        every { mockSettings.defaultPassword } returns ""
        every { mockSettingsRepository.getSettings() } returns flowOf(mockSettings)

        repository = NetworkCredentialsRepositoryImpl(
            context = mockContext,
            dao = mockDao,
            resourceDao = mockResourceDao,
            settingsRepository = mockSettingsRepository,
            applicationScope = TestScope()
        )
    }

    @Test
    fun `insert delegates to DAO`() = runTest {
        // Setup
        val credential = NetworkCredentialsEntity(
            credentialId = "test-id",
            type = "SMB",
            server = "192.168.1.1",
            port = 445,
            username = "user",
            encryptedPassword = "encrypted",
            shareName = "share",
            domain = ""
        )
        
        coEvery { mockDao.insert(credential) } returns 1L

        // Execute
        val result = repository.insert(credential)

        // Verify
        assertEquals(1L, result)
        coVerify(exactly = 1) { mockDao.insert(credential) }
    }

    @Test
    fun `getByCredentialId returns entity from DAO`() = runTest {
        // Setup
        val credentialId = "test-id"
        val expectedEntity = NetworkCredentialsEntity(
            credentialId = credentialId,
            type = "SMB",
            server = "192.168.1.1",
            port = 445,
            username = "user",
            encryptedPassword = "encrypted",
            shareName = "share",
            domain = ""
        )
        
        coEvery { mockDao.getCredentialsById(credentialId) } returns expectedEntity

        // Execute
        val result = repository.getByCredentialId(credentialId)

        // Verify
        assertNotNull(result)
        assertEquals(credentialId, result?.credentialId)
        coVerify(exactly = 1) { mockDao.getCredentialsById(credentialId) }
    }

    @Test
    fun `update delegates to DAO`() = runTest {
        // Setup
        val credential = NetworkCredentialsEntity(
            credentialId = "test-id",
            type = "SMB",
            server = "192.168.1.1",
            port = 445,
            username = "newuser",
            encryptedPassword = "newencrypted",
            shareName = "share",
            domain = ""
        )
        
        coEvery { mockDao.update(credential) } just Runs

        // Execute
        repository.update(credential)

        // Verify
        coVerify(exactly = 1) { mockDao.update(credential) }
    }

    @Test
    fun `delete delegates to DAO deleteByCredentialId`() = runTest {
        // Setup
        val credential = NetworkCredentialsEntity(
            credentialId = "test-id",
            type = "SMB",
            server = "192.168.1.1",
            port = 445,
            username = "user",
            encryptedPassword = "encrypted",
            shareName = "share",
            domain = ""
        )
        
        coEvery { mockDao.deleteByCredentialId(credential.credentialId) } just Runs

        // Execute
        repository.delete(credential)

        // Verify
        coVerify(exactly = 1) { mockDao.deleteByCredentialId(credential.credentialId) }
    }
}
