package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Smoke tests for GetMediaFilesUseCase that align with current Clean Architecture design.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetMediaFilesUseCaseTest {

    private val mediaScannerFactory: MediaScannerFactory = mockk()
    private val mediaScanner: MediaScanner = mockk()
    private lateinit var useCase: GetMediaFilesUseCase

    @Before
    fun setUp() {
        every { mediaScannerFactory.getScanner(any()) } returns mediaScanner
        useCase = GetMediaFilesUseCase(mediaScannerFactory)
    }

    @After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    @Test
    fun `returns files sorted by name when folder small`() = runTest {
        val resource = createResource(ResourceType.LOCAL)
        val unsorted = listOf(
            mediaFile("charlie.jpg"),
            mediaFile("alpha.jpg"),
            mediaFile("bravo.jpg")
        )

        coEvery {
            mediaScanner.scanFolder(
                path = resource.path,
                supportedTypes = resource.supportedMediaTypes,
                sizeFilter = any(),
                credentialsId = resource.credentialsId,
                onProgress = any()
            )
        } returns unsorted

        val result = useCase(
            resource = resource,
            sortMode = SortMode.NAME_ASC
        ).first()

        assertEquals(listOf("alpha.jpg", "bravo.jpg", "charlie.jpg"), result.map { it.name })
    }

    @Test
    fun `keeps original order when folder exceeds threshold`() = runTest {
        val resource = createResource(ResourceType.LOCAL)
        val largeList = (1..1100).map { index ->
            mediaFile(name = "file${index.toString().padStart(4, '0')}.jpg")
        }

        coEvery {
            mediaScanner.scanFolder(
                path = resource.path,
                supportedTypes = resource.supportedMediaTypes,
                sizeFilter = any(),
                credentialsId = resource.credentialsId,
                onProgress = any()
            )
        } returns largeList

        val result = useCase(
            resource = resource,
            sortMode = SortMode.DATE_DESC
        ).first()

        assertSame(largeList, result)
    }

    private fun createResource(type: ResourceType): MediaResource {
        return MediaResource(
            id = 1L,
            name = "Test",
            path = "/test",
            type = type,
            supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO)
        )
    }

    private fun mediaFile(name: String, created: Long = 0L): MediaFile {
        return MediaFile(
            name = name,
            path = "/test/$name",
            type = MediaType.IMAGE,
            size = 100,
            createdDate = created
        )
    }
}
