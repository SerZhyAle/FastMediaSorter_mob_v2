package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.sza.fastmediasorter.core.util.UriPathResolver
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProvisionDownloadsDestinationUseCaseTest {

    private val context: Context = mockk(relaxed = true)
    private val resourceRepository: ResourceRepository = mockk(relaxed = true)
    private val resolveResourceIconUseCase: ResolveResourceIconUseCase = mockk(relaxed = true)

    private lateinit var useCase: ProvisionDownloadsDestinationUseCase
    private lateinit var downloadsDir: File

    private companion object {
        const val SAF_DOWNLOADS_URI = "content://com.android.externalstorage.documents/tree/primary%3ADownload"
    }

    @Before
    fun setUp() {
        mockkObject(UriPathResolver)

        downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()

        every { context.getString(any()) } returns "Downloads"
        every { resolveResourceIconUseCase(any(), any(), any()) } returns "ico-05-001"

        useCase = ProvisionDownloadsDestinationUseCase(
            context = context,
            resourceRepository = resourceRepository,
            resolveResourceIconUseCase = resolveResourceIconUseCase
        )
    }

    @After
    fun tearDown() {
        unmockkObject(UriPathResolver)
    }

    @Test
    fun `invoke returns false when raw downloads destination already exists`() = runTest {
        val existing = listOf(
            MediaResource(
                id = 1L,
                name = "Downloads",
                path = downloadsDir.absolutePath,
                type = ResourceType.LOCAL,
                isDestination = true
            )
        )
        coEvery { resourceRepository.getAllResourcesSync() } returns existing

        val result = useCase()

        assertFalse(result)
        coVerify(exactly = 0) { resourceRepository.addResource(any()) }
    }

    @Test
    fun `invoke returns false when reconnected SAF downloads destination already exists`() = runTest {
        val safUri = Uri.parse(SAF_DOWNLOADS_URI)
        every { UriPathResolver.getPath(context, safUri) } returns downloadsDir.absolutePath

        val existing = listOf(
            MediaResource(
                id = 1L,
                name = "Downloads",
                path = SAF_DOWNLOADS_URI,
                type = ResourceType.LOCAL,
                isDestination = true
            )
        )
        coEvery { resourceRepository.getAllResourcesSync() } returns existing

        val result = useCase()

        assertFalse(result)
        coVerify(exactly = 0) { resourceRepository.addResource(any()) }
    }

    @Test
    fun `invoke collapses duplicates keeping reconnected SAF destination over raw path`() = runTest {
        val safUri = Uri.parse(SAF_DOWNLOADS_URI)
        every { UriPathResolver.getPath(context, safUri) } returns downloadsDir.absolutePath

        val existing = listOf(
            MediaResource(
                id = 1L,
                name = "Downloads",
                path = SAF_DOWNLOADS_URI,
                type = ResourceType.LOCAL,
                isDestination = true
            ),
            MediaResource(
                id = 2L,
                name = "Downloads",
                path = downloadsDir.absolutePath,
                type = ResourceType.LOCAL,
                isDestination = true
            )
        )
        coEvery { resourceRepository.getAllResourcesSync() } returns existing

        val result = useCase()

        assertFalse(result)
        coVerify(exactly = 0) { resourceRepository.addResource(any()) }
        // The raw path duplicate (id=2) should be deleted, keeping SAF (id=1)
        coVerify(exactly = 1) { resourceRepository.deleteResource(2L) }
        coVerify(exactly = 0) { resourceRepository.deleteResource(1L) }
    }

    @Test
    fun `invoke creates Downloads destination when directory exists and no existing destination match`() = runTest {
        val existing = listOf(
            MediaResource(
                id = 1L,
                name = "Camera",
                path = "/storage/emulated/0/DCIM",
                type = ResourceType.LOCAL,
                isDestination = false
            )
        )
        coEvery { resourceRepository.getAllResourcesSync() } returns existing
        coEvery { resourceRepository.addResource(any()) } returns 10L

        val result = useCase()

        assertTrue(result)
        coVerify(
            exactly = 1
        ) { resourceRepository.addResource(match { it.path == downloadsDir.absolutePath && it.isDestination }) }
    }
}
