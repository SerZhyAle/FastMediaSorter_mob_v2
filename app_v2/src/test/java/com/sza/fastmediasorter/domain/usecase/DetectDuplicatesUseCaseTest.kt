package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.DuplicateDetectionResult
import com.sza.fastmediasorter.domain.model.DuplicateScanProgress
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScanPhase
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DetectDuplicatesUseCaseTest {

    private val getMediaFiles = mockk<GetMediaFilesUseCase>()
    private val computeHash = mockk<ComputeFileHashUseCase>()
    private lateinit var useCase: DetectDuplicatesUseCase

    @Before
    fun setup() {
        useCase = DetectDuplicatesUseCase(getMediaFiles, computeHash)
    }

    private fun stubFiles(resource: MediaResource, files: List<MediaFile>) {
        every {
            getMediaFiles(eq(resource), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns flowOf(files)
    }

    private suspend fun run(resources: List<MediaResource>): List<Any> =
        useCase.detect(resources).toList()

    @Test
    fun `final result emitted as DuplicateDetectionResult`() = runTest {
        val resource = createMediaResource(id = 1L)
        stubFiles(resource, emptyList())

        val emissions = run(listOf(resource))

        assertTrue(emissions.last() is DuplicateDetectionResult)
    }

    @Test
    fun `zero-byte files are excluded from scan`() = runTest {
        val resource = createMediaResource(id = 1L)
        stubFiles(resource, listOf(createMediaFile(name = "empty", size = 0L)))

        val result = run(listOf(resource)).last() as DuplicateDetectionResult

        assertEquals(0, result.totalFilesScanned)
        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun `unique sizes never reach hashing and yield no duplicates`() = runTest {
        val resource = createMediaResource(id = 1L)
        stubFiles(
            resource,
            listOf(
                createMediaFile(name = "a", size = 100L, resourceId = 1L),
                createMediaFile(name = "b", size = 200L, resourceId = 1L),
            ),
        )

        val result = run(listOf(resource)).last() as DuplicateDetectionResult

        assertEquals(2, result.totalFilesScanned)
        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun `same size but different quick hash are not duplicates`() = runTest {
        val resource = createMediaResource(id = 1L)
        val a = createMediaFile(name = "a", path = "/a", size = 100L, resourceId = 1L)
        val b = createMediaFile(name = "b", path = "/b", size = 100L, resourceId = 1L)
        stubFiles(resource, listOf(a, b))
        coEvery { computeHash.compute(a, any(), DetectDuplicatesUseCase.QUICK_HASH_BYTES) } returns "QA"
        coEvery { computeHash.compute(b, any(), DetectDuplicatesUseCase.QUICK_HASH_BYTES) } returns "QB"

        val result = run(listOf(resource)).last() as DuplicateDetectionResult

        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun `identical size quick hash and full hash form a duplicate group with wasted bytes`() = runTest {
        val resource = createMediaResource(id = 1L)
        val a = createMediaFile(name = "a", path = "/a", size = 100L, resourceId = 1L)
        val b = createMediaFile(name = "b", path = "/b", size = 100L, resourceId = 1L)
        stubFiles(resource, listOf(a, b))
        coEvery { computeHash.compute(a, any(), DetectDuplicatesUseCase.QUICK_HASH_BYTES) } returns "Q"
        coEvery { computeHash.compute(b, any(), DetectDuplicatesUseCase.QUICK_HASH_BYTES) } returns "Q"
        coEvery { computeHash.compute(a, any(), -1L) } returns "FULL"
        coEvery { computeHash.compute(b, any(), -1L) } returns "FULL"

        val result = run(listOf(resource)).last() as DuplicateDetectionResult

        assertEquals(1, result.groups.size)
        val group = result.groups.single()
        assertEquals(2, group.files.size)
        assertEquals(100L, group.fileSize)
        assertEquals("FULL", group.fullHash)
        assertEquals(100L, result.totalWastedBytes)
    }

    @Test
    fun `quick hash survivors that differ on full hash are not grouped`() = runTest {
        val resource = createMediaResource(id = 1L)
        val a = createMediaFile(name = "a", path = "/a", size = 100L, resourceId = 1L)
        val b = createMediaFile(name = "b", path = "/b", size = 100L, resourceId = 1L)
        stubFiles(resource, listOf(a, b))
        coEvery { computeHash.compute(a, any(), DetectDuplicatesUseCase.QUICK_HASH_BYTES) } returns "Q"
        coEvery { computeHash.compute(b, any(), DetectDuplicatesUseCase.QUICK_HASH_BYTES) } returns "Q"
        coEvery { computeHash.compute(a, any(), -1L) } returns "F1"
        coEvery { computeHash.compute(b, any(), -1L) } returns "F2"

        val result = run(listOf(resource)).last() as DuplicateDetectionResult

        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun `null quick hash drops the file from candidacy`() = runTest {
        val resource = createMediaResource(id = 1L)
        val a = createMediaFile(name = "a", path = "/a", size = 100L, resourceId = 1L)
        val b = createMediaFile(name = "b", path = "/b", size = 100L, resourceId = 1L)
        stubFiles(resource, listOf(a, b))
        coEvery { computeHash.compute(a, any(), DetectDuplicatesUseCase.QUICK_HASH_BYTES) } returns null
        coEvery { computeHash.compute(b, any(), DetectDuplicatesUseCase.QUICK_HASH_BYTES) } returns "Q"

        val result = run(listOf(resource)).last() as DuplicateDetectionResult

        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun `listing failure for one resource is swallowed`() = runTest {
        val good = createMediaResource(id = 1L)
        val bad = createMediaResource(id = 2L)
        every {
            getMediaFiles(eq(bad), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("io")
        stubFiles(good, listOf(createMediaFile(name = "x", size = 50L, resourceId = 1L)))

        val result = run(listOf(good, bad)).last() as DuplicateDetectionResult

        assertEquals(1, result.totalFilesScanned)
    }

    @Test
    fun `progress phases are emitted in order`() = runTest {
        val resource = createMediaResource(id = 1L)
        stubFiles(resource, emptyList())

        val progresses = run(listOf(resource)).filterIsInstance<DuplicateScanProgress>()

        assertEquals(ScanPhase.LISTING, progresses.first().phase)
        assertTrue(progresses.any { it.phase == ScanPhase.DONE })
    }
}
