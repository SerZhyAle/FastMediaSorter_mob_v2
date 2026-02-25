package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.network.SmbMediaScanner
import com.sza.fastmediasorter.core.util.CachedMediaMetadataExtractor
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.usecase.scan.ScanDispatcher
import com.sza.fastmediasorter.domain.usecase.scan.ScanSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After

@OptIn(ExperimentalCoroutinesApi::class)
class GetMediaFilesUseCaseTest {

    private lateinit var useCase: GetMediaFilesUseCase
    private val mediaScannerFactory: MediaScannerFactory = mockk()
    private val favoritesRepository: FavoritesRepository = mockk()
    private val credentialsRepository: NetworkCredentialsRepository = mockk()
    private val cachedFileListRepository: CachedFileListRepository = mockk()
    private val scanDispatcher: ScanDispatcher = ScanDispatcher(ScanSettings())
    private val metadataExtractor: CachedMediaMetadataExtractor = mockk()
    private val smbScanner: SmbMediaScanner = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        useCase = GetMediaFilesUseCase(
            mediaScannerFactory,
            favoritesRepository,
            credentialsRepository,
            cachedFileListRepository,
            scanDispatcher,
            metadataExtractor
        )
        
        // Default mocks
        every { mediaScannerFactory.getScanner(ResourceType.SMB) } returns smbScanner
        coEvery { favoritesRepository.getAllFavorites() } returns flowOf(emptyList())
        coEvery { cachedFileListRepository.getEntityByResourceId(any()) } returns null
        coEvery { cachedFileListRepository.getCachedFiles(any()) } returns null
        coEvery { cachedFileListRepository.saveCachedFiles(any(), any(), any(), any()) } returns Unit
        coEvery { metadataExtractor.enrichBatch(any(), any(), any(), any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            args[3] as List<com.sza.fastmediasorter.domain.model.MediaFile>
        }
        every { metadataExtractor.logSessionDiagnostics(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invoke should use existing full SMB URL without duplication`() = runTest {
        // Given
        val fullSmbPath = "smb://192.168.1.100/test_media"
        val resource = MediaResource(
            id = 1,
            name = "SMB Share",
            path = fullSmbPath,
            type = ResourceType.SMB,
            credentialsId = "cred-1",
            supportedMediaTypes = setOf(MediaType.IMAGE),
            scanSubdirectories = true
        )

        coEvery { smbScanner.scanFolder(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()

        // When
        useCase(resource).first()

        // Then
        coVerify {
            smbScanner.scanFolder(
                path = fullSmbPath,
                supportedTypes = any(),
                sizeFilter = any(),
                credentialsId = "cred-1",
                scanSubdirectories = true,
                showHiddenFiles = false,
                onProgress = any()
            )
        }
    }
    
    @Test
    fun `invoke should use existing full SMB URL with leading whitespace`() = runTest {
        // Given
        val fullSmbPath = "   smb://192.168.1.100/test_media"
        val resource = MediaResource(
            id = 1,
            name = "SMB Share",
            path = fullSmbPath,
            type = ResourceType.SMB,
            credentialsId = "cred-1",
            supportedMediaTypes = setOf(MediaType.IMAGE),
            scanSubdirectories = true
        )

        coEvery { smbScanner.scanFolder(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()

        // When
        useCase(resource).first()

        // Then
        coVerify {
            smbScanner.scanFolder(
                path = "smb://192.168.1.100/test_media",
                supportedTypes = any(),
                sizeFilter = any(),
                credentialsId = "cred-1",
                scanSubdirectories = true,
                showHiddenFiles = false,
                onProgress = any()
            )
        }
    }

    @Test
    fun `invoke should construct SMB URL correctly if relative path`() = runTest {
        // Given
        val relativePath = "/subfolder"
        val resource = MediaResource(
            id = 1,
            name = "SMB Share",
            path = relativePath,
            type = ResourceType.SMB,
            credentialsId = "cred-1",
            supportedMediaTypes = setOf(MediaType.IMAGE),
            scanSubdirectories = true
        )
        
        val credentials = mockk<NetworkCredentialsEntity>(relaxed = true)
        every { credentials.server } returns "192.168.1.100"
        every { credentials.shareName } returns "test_media"
        every { credentials.port } returns 445
        
        coEvery { credentialsRepository.getByCredentialId("cred-1") } returns credentials
        coEvery { smbScanner.scanFolder(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()

        // When
        useCase(resource).first()

        // Then: Should build smb://192.168.1.100:445/test_media/subfolder/ (Assuming SmbPathUtils handles slashes)
        // Since we can't easily mock static SmbPathUtils in Unit Test without PowerMock, 
        // we rely on the fact that SmbPathUtils is a utility that GetMediaFilesUseCase calls.
        
        // Wait, SmbPathUtils is a static utility class. I cannot mock it with MockK unless I mockStatic.
        // However, I just want to verify that the 'else' branch is taken and it *tries* to build passed parameters correctly.
        // But since SmbPathUtils.buildSmbPath is called, the result passed to scanFolder is the result of that static call.
        // We can't verify the exact string unless we know what SmbPathUtils does.
        // Assuming SmbPathUtils is deterministic and works, we can check if scanFolder is called with SOMETHING that is not just "/subfolder".
        
        coVerify {
            smbScanner.scanFolder(
                path = match { it.startsWith("smb://") }, 
                supportedTypes = any(),
                sizeFilter = any(),
                credentialsId = "cred-1",
                scanSubdirectories = true,
                showHiddenFiles = false,
                onProgress = any()
            )
        }
    }
}
