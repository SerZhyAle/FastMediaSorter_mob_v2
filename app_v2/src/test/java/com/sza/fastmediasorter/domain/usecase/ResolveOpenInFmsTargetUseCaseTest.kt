package com.sza.fastmediasorter.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testutil.testMediaCapabilities
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveOpenInFmsTargetUseCaseTest {

    private val resolveLocalPath = mockk<ResolveLocalPathFromUriUseCase>()
    private val ensureAllFiles = mockk<EnsureAllFilesPredefinedResourceUseCase>()
    private val resolveIcon = mockk<ResolveResourceIconUseCase>(relaxed = true)
    private val addResource = mockk<AddResourceUseCase>()
    private val resourceRepository = mockk<ResourceRepository>()
    private val settingsRepository = mockk<SettingsRepository>()

    private val useCase = ResolveOpenInFmsTargetUseCase(
        resolveLocalPath,
        ensureAllFiles,
        resolveIcon,
        addResource,
        resourceRepository,
        settingsRepository,
        testMediaCapabilities()
    )

    private val uri = mockk<Uri>()

    private fun localResource(id: Long, path: String, profile: ResourceProfile = ResourceProfile.NONE) =
        MediaResource(id = id, name = "n$id", path = path, type = ResourceType.LOCAL, profile = profile)

    @Test
    fun `not-local uri resolves to NotResolvable`() = runTest {
        coEvery { resolveLocalPath(uri) } returns LocalPathResolution.NotLocal

        val result = useCase(uri, MediaType.IMAGE)

        assertEquals(OpenInFmsTarget.NotResolvable, result)
    }

    @Test
    fun `existing All Files resource is preferred`() = runTest {
        coEvery { resolveLocalPath(uri) } returns
            LocalPathResolution.Local("/storage/emulated/0/Pictures/p.jpg", "/storage/emulated/0/Pictures")
        val allFiles = localResource(7, "/storage/emulated/0", ResourceProfile.ALL_FILES)
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(allFiles)
        every { ensureAllFiles.isPredefinedResource(allFiles) } returns true

        val result = useCase(uri, MediaType.IMAGE)

        assertEquals(
            OpenInFmsTarget.Resolved(7, "/storage/emulated/0/Pictures/p.jpg"),
            result
        )
    }

    @Test
    fun `aggregate resource is used when All Files absent`() = runTest {
        coEvery { resolveLocalPath(uri) } returns
            LocalPathResolution.Local("/storage/emulated/0/Pictures/p.jpg", "/storage/emulated/0/Pictures")
        val aggregate = localResource(11, LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES)
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(aggregate)
        every { ensureAllFiles.isPredefinedResource(any()) } returns false

        val result = useCase(uri, MediaType.IMAGE)

        assertEquals(
            OpenInFmsTarget.Resolved(11, "/storage/emulated/0/Pictures/p.jpg"),
            result
        )
    }

    @Test
    fun `no match creates a folder resource named by folder with the file family type`() = runTest {
        coEvery { resolveLocalPath(uri) } returns
            LocalPathResolution.Local("/storage/emulated/0/MyVids/v.mp4", "/storage/emulated/0/MyVids")
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        every { ensureAllFiles.isPredefinedResource(any()) } returns false
        coEvery { resourceRepository.getLocalResourceByPath("/storage/emulated/0/MyVids") } returns null
        every { settingsRepository.getSettings() } returns flowOf(AppSettings(allFiles = false))
        val captured = slot<MediaResource>()
        coEvery { addResource(capture(captured), addToTop = true) } returns Result.success(42L)

        val result = useCase(uri, MediaType.VIDEO)

        assertEquals(OpenInFmsTarget.Resolved(42L, "/storage/emulated/0/MyVids/v.mp4"), result)
        assertEquals("MyVids", captured.captured.name)
        assertEquals("/storage/emulated/0/MyVids", captured.captured.path)
        assertEquals(setOf(MediaType.VIDEO), captured.captured.supportedMediaTypes)
        assertTrue(!captured.captured.allFiles)
    }

    @Test
    fun `existing folder resource is reused without creating a duplicate`() = runTest {
        coEvery { resolveLocalPath(uri) } returns
            LocalPathResolution.Local("/storage/emulated/0/MyVids/v.mp4", "/storage/emulated/0/MyVids")
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        every { ensureAllFiles.isPredefinedResource(any()) } returns false
        coEvery { resourceRepository.getLocalResourceByPath("/storage/emulated/0/MyVids") } returns
            localResource(99, "/storage/emulated/0/MyVids")

        val result = useCase(uri, MediaType.VIDEO)

        assertEquals(OpenInFmsTarget.Resolved(99, "/storage/emulated/0/MyVids/v.mp4"), result)
    }

    @Test
    fun `all-files setting widens created resource to every type with ALL_FILES profile`() = runTest {
        coEvery { resolveLocalPath(uri) } returns
            LocalPathResolution.Local("/storage/emulated/0/Mixed/v.mp4", "/storage/emulated/0/Mixed")
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        every { ensureAllFiles.isPredefinedResource(any()) } returns false
        coEvery { resourceRepository.getLocalResourceByPath("/storage/emulated/0/Mixed") } returns null
        every { settingsRepository.getSettings() } returns flowOf(AppSettings(allFiles = true))
        val captured = slot<MediaResource>()
        coEvery { addResource(capture(captured), addToTop = true) } returns Result.success(5L)

        val result = useCase(uri, MediaType.VIDEO)

        assertEquals(OpenInFmsTarget.Resolved(5L, "/storage/emulated/0/Mixed/v.mp4"), result)
        assertEquals(MediaType.entries.toSet(), captured.captured.supportedMediaTypes)
        assertTrue(captured.captured.allFiles)
        assertEquals(ResourceProfile.ALL_FILES, captured.captured.profile)
    }
}
