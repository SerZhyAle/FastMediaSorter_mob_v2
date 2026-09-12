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
import com.sza.fastmediasorter.domain.usecase.ResolveResourceIconUseCase
import com.sza.fastmediasorter.testutil.testMediaCapabilities
import com.sza.fastmediasorter.util.VirtualPathUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProvisionDefaultResourcesUseCaseTest {

    private val context: Context = mockk(relaxed = true)
    private val resourceRepository: ResourceRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val resolveResourceIconUseCase: ResolveResourceIconUseCase = ResolveResourceIconUseCase()

    private lateinit var useCase: ProvisionDefaultResourcesUseCase

    private companion object {
        const val DOWNLOADS_PATH = "/storage/emulated/0/Download"
    }

    @Before
    fun setUp() {
        // Stub string resources
        every { context.getString(any()) } returns "Stub"

        useCase = ProvisionDefaultResourcesUseCase(context, resourceRepository, settingsRepository, resolveResourceIconUseCase, testMediaCapabilities())
    }

    // ── Skip when all predefined paths already present ───────

    @Test
    fun `invoke returns false when all predefined paths already exist`() = runTest {
        val settings = AppSettings(
            supportAudio = true, supportVideos = true,
            supportText = true, supportPdf = true, supportEpub = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)
        val existing = listOf(
            MediaResource(name = "Recent", path = LocalMediaScanner.VIRTUAL_PATH_RECENT, type = ResourceType.LOCAL),
            MediaResource(name = "All Music", path = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO, type = ResourceType.LOCAL),
            MediaResource(name = "All Videos", path = LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO, type = ResourceType.LOCAL),
            MediaResource(name = "Camera", path = LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS, type = ResourceType.LOCAL),
            MediaResource(name = "All Images", path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES, type = ResourceType.LOCAL),
            MediaResource(name = "All Docs", path = LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS, type = ResourceType.LOCAL),
        )
        coEvery { resourceRepository.getAllResources() } returns flowOf(existing)

        val result = useCase()

        assertFalse(result)
        coVerify(exactly = 0) { resourceRepository.addResource(any()) }
    }

    // ── Partial provisioning repair ───────────────────────────
    // Regression: viewModelScope cancelled after creating Recent but before the rest.
    // The next launch must create the 5 missing resources instead of skipping all.

    @Test
    fun `invoke provisions missing resources when only Recent was created before cancellation`() = runTest {
        val settings = AppSettings(
            supportAudio = true, supportVideos = true,
            supportText = true, supportPdf = true, supportEpub = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)
        val existing = listOf(
            MediaResource(name = "Recent", path = LocalMediaScanner.VIRTUAL_PATH_RECENT, type = ResourceType.LOCAL)
        )
        coEvery { resourceRepository.getAllResources() } returns flowOf(existing)
        coEvery { resourceRepository.addResource(any()) } returns 1L

        val result = useCase()

        assertTrue(result)
        // Recent already exists; the 5 missing predefined resources must be created
        coVerify(exactly = 5) { resourceRepository.addResource(any()) }
    }

    @Test
    fun `invoke provisions missing resources when only non-virtual resources exist`() = runTest {
        val settings = AppSettings(
            supportAudio = true, supportVideos = true,
            supportText = true, supportPdf = true, supportEpub = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)
        val existing = listOf(
            MediaResource(name = "Downloads", path = "/storage/emulated/0/Download", type = ResourceType.LOCAL)
        )
        coEvery { resourceRepository.getAllResources() } returns flowOf(existing)
        coEvery { resourceRepository.addResource(any()) } returns 1L

        val result = useCase()

        assertTrue(result)
        // Downloads is not a predefined virtual path - all 6 must be created
        coVerify(exactly = 6) { resourceRepository.addResource(any()) }
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
            supportEpub = false,
            supportOfficeDocuments = false
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
        assertEquals(setOf(MediaType.TEXT, MediaType.EPUB, MediaType.OFFICE_DOCUMENT), docsResource.supportedMediaTypes)
    }

    // ── S2634: two passes overlapping in time ─────────────────
    // The read yields before it answers, so the second pass reaches the snapshot while the first
    // has decided but not yet written. Without serialisation both passes see an empty table and
    // both write all six records, which is what put six virtual resources on screen twice.

    @Test
    fun `two overlapping passes create each predefined resource exactly once`() = runTest {
        val settings = AppSettings(
            supportAudio = true, supportVideos = true,
            supportText = true, supportPdf = true, supportEpub = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        val stored = mutableListOf<MediaResource>()
        every { resourceRepository.getAllResources() } answers {
            // Captured when the READ is issued, delivered after a suspension point: that is what a
            // real query does, and emitting `stored` at collection time instead would hand the
            // second pass a snapshot the first pass had already written into - which is exactly the
            // staleness this test exists to catch.
            val snapshot = stored.toList()
            flow {
                yield()
                emit(snapshot)
            }
        }
        coEvery { resourceRepository.addResource(any()) } coAnswers {
            stored += firstArg<MediaResource>()
            stored.size.toLong()
        }

        coroutineScope {
            awaitAll(async { useCase() }, async { useCase() })
        }

        assertEquals(6, stored.size)
        assertEquals(6, stored.map { it.path }.toSet().size)
    }

    // ── S2634: an install that already ran the race heals itself ──

    @Test
    fun `invoke deletes duplicate predefined records and keeps the lowest id`() = runTest {
        val settings = AppSettings(
            supportAudio = true, supportVideos = true,
            supportText = true, supportPdf = true, supportEpub = true
        )
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)
        val existing = listOf(
            resourceAt(1L, LocalMediaScanner.VIRTUAL_PATH_RECENT),
            resourceAt(7L, LocalMediaScanner.VIRTUAL_PATH_RECENT),
            resourceAt(2L, DOWNLOADS_PATH),
            resourceAt(8L, DOWNLOADS_PATH)
        )
        coEvery { resourceRepository.getAllResources() } returns flowOf(existing)
        coEvery { resourceRepository.addResource(any()) } returns 1L

        useCase()

        coVerify(exactly = 1) { resourceRepository.deleteResource(7L) }
        coVerify(exactly = 0) { resourceRepository.deleteResource(1L) }
        // A repeated non-predefined path is not this use case's to judge - the user may own it.
        coVerify(exactly = 0) { resourceRepository.deleteResource(8L) }
    }

    private fun resourceAt(id: Long, path: String) =
        MediaResource(id = id, name = "Stub", path = path, type = ResourceType.LOCAL)

    // ── All resources use LOCAL type and isWritable=false ──────

    @Test
    fun `Recent resource is provisioned with allFiles true`() = runTest {
        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        val settings = AppSettings(supportAudio = true, supportVideos = true)
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        val captured = mutableListOf<MediaResource>()
        coEvery { resourceRepository.addResource(capture(captured)) } returns 1L

        useCase()

        val recent = captured.first { it.path == LocalMediaScanner.VIRTUAL_PATH_RECENT }
        assertTrue("Recent must have allFiles=true by default (S0059)", recent.allFiles)
    }

    // ── All resources use LOCAL type; aggregates are writable destinations, Recent is not ──

    @Test
    fun `all provisioned resources are LOCAL and writable only when aggregate`() = runTest {
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
            // Aggregate virtual resources (all_audio/video/images/docs, camera) are writable
            // drop targets; only Recent is read-only.
            assertEquals(
                "${resource.path} writability must follow aggregate-virtual status",
                VirtualPathUtils.isAggregateVirtualPath(resource.path),
                resource.isWritable
            )
        }
    }
}
