package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProvisionDefaultResourcesUseCaseTest {

    private val context: Context = mockk(relaxed = true)
    private val resourceRepository: ResourceRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)

    private lateinit var useCase: ProvisionDefaultResourcesUseCase

    @Before
    fun setUp() {
        // Stub string resources
        every { context.getString(any()) } returns "Stub"

        useCase = ProvisionDefaultResourcesUseCase(context, resourceRepository, settingsRepository)
    }

    // ── Skip when DB is non-empty ────────────────────────────

    @Test
    fun `invoke returns false when resources already exist`() = runTest {
        val existing = listOf(
            MediaResource(name = "Existing", path = "/some/path", type = ResourceType.LOCAL)
        )
        coEvery { resourceRepository.getAllResources() } returns flowOf(existing)

        val result = useCase()

        assertFalse(result)
        coVerify(exactly = 0) { resourceRepository.addResource(any()) }
    }

    // ── Full provisioning (all features enabled) ─────────────

    @Test
    fun `invoke creates 6 resources when DB is empty and all features enabled`() = runTest {
        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        val settings = AppSettings(
            supportAudio = true,
            supportVideos = true,
            supportText = true,
            supportPdf = true,
            supportEpub = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)
        coEvery { resourceRepository.addResource(any()) } returns 1L

        val result = useCase()

        assertTrue(result)
        coVerify(exactly = 6) { resourceRepository.addResource(any()) }
    }

    // ── DisplayOrder increments sequentially ──────────────────

    @Test
    fun `invoke assigns sequential displayOrder starting from 0`() = runTest {
        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        val settings = AppSettings(
            supportAudio = true,
            supportVideos = true,
            supportText = true,
            supportPdf = true,
            supportEpub = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        val captured = mutableListOf<MediaResource>()
        coEvery { resourceRepository.addResource(capture(captured)) } returns 1L

        useCase()

        assertEquals(6, captured.size)
        assertEquals(0, captured[0].displayOrder)
        assertEquals(1, captured[1].displayOrder)
        assertEquals(2, captured[2].displayOrder)
        assertEquals(3, captured[3].displayOrder)
        assertEquals(4, captured[4].displayOrder)
        assertEquals(5, captured[5].displayOrder)
    }

    // ── Virtual paths are correct ─────────────────────────────

    @Test
    fun `invoke creates resources with correct virtual paths`() = runTest {
        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        val settings = AppSettings(
            supportAudio = true,
            supportVideos = true,
            supportText = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        val captured = mutableListOf<MediaResource>()
        coEvery { resourceRepository.addResource(capture(captured)) } returns 1L

        useCase()

        assertEquals(LocalMediaScanner.VIRTUAL_PATH_RECENT, captured[0].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO, captured[1].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO, captured[2].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS, captured[3].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES, captured[4].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS, captured[5].path)
    }

    // ── Profile assignment ────────────────────────────────────

    @Test
    fun `invoke assigns correct profiles to each resource`() = runTest {
        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        val settings = AppSettings(
            supportAudio = true,
            supportVideos = true,
            supportPdf = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        val captured = mutableListOf<MediaResource>()
        coEvery { resourceRepository.addResource(capture(captured)) } returns 1L

        useCase()

        assertEquals(ResourceProfile.NONE, captured[0].profile)
        assertEquals(ResourceProfile.AUDIO_LIBRARY, captured[1].profile)
        assertEquals(ResourceProfile.VIDEO_LIBRARY, captured[2].profile)
        assertEquals(ResourceProfile.PHOTO_STORAGE, captured[3].profile)
        assertEquals(ResourceProfile.PHOTO_STORAGE, captured[4].profile)
        assertEquals(ResourceProfile.DOCUMENTS, captured[5].profile)
    }

    // ── Audio disabled → 3 resources ──────────────────────────

    @Test
    fun `invoke skips audio resource when supportAudio is false`() = runTest {
        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        val settings = AppSettings(
            supportAudio = false,
            supportVideos = true,
            supportText = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        val captured = mutableListOf<MediaResource>()
        coEvery { resourceRepository.addResource(capture(captured)) } returns 1L

        useCase()

        assertEquals(5, captured.size)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_RECENT, captured[0].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO, captured[1].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS, captured[2].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES, captured[3].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS, captured[4].path)
    }

    // ── Videos disabled → 3 resources ─────────────────────────

    @Test
    fun `invoke skips video resource when supportVideos is false`() = runTest {
        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        val settings = AppSettings(
            supportAudio = true,
            supportVideos = false,
            supportText = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        val captured = mutableListOf<MediaResource>()
        coEvery { resourceRepository.addResource(capture(captured)) } returns 1L

        useCase()

        assertEquals(5, captured.size)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_RECENT, captured[0].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO, captured[1].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS, captured[2].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES, captured[3].path)
        assertEquals(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS, captured[4].path)
    }

    // ── No doc types enabled → skip documents ─────────────────

    @Test
    fun `invoke skips documents when no doc types are enabled`() = runTest {
        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        val settings = AppSettings(
            supportAudio = true,
            supportVideos = true,
            supportText = false,
            supportPdf = false,
            supportEpub = false
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        val captured = mutableListOf<MediaResource>()
        coEvery { resourceRepository.addResource(capture(captured)) } returns 1L

        useCase()

        assertEquals(5, captured.size)
        assertTrue(captured.none { it.path == LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS })
    }

    // ── Media types on document resource ──────────────────────

    @Test
    fun `documents resource includes only enabled doc media types`() = runTest {
        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        val settings = AppSettings(
            supportAudio = true,
            supportVideos = true,
            supportText = true,
            supportPdf = false,
            supportEpub = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        val captured = mutableListOf<MediaResource>()
        coEvery { resourceRepository.addResource(capture(captured)) } returns 1L

        useCase()

        val docsResource = captured.first { it.path == LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS }
        assertEquals(setOf(MediaType.TEXT, MediaType.EPUB), docsResource.supportedMediaTypes)
    }

    // ── All resources use LOCAL type and isWritable=false ──────

    @Test
    fun `all provisioned resources are LOCAL type and not writable`() = runTest {
        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        val settings = AppSettings(
            supportAudio = true,
            supportVideos = true,
            supportText = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        val captured = mutableListOf<MediaResource>()
        coEvery { resourceRepository.addResource(capture(captured)) } returns 1L

        useCase()

        captured.forEach { resource ->
            assertEquals("${resource.path} should be LOCAL", ResourceType.LOCAL, resource.type)
            assertFalse("${resource.path} should not be writable", resource.isWritable)
        }
    }
}
