package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.data.local.staging.StagedKind
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveDrawingUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fileOperationUseCase = mockk<FileOperationUseCase>()
    private val stagingRegistry = LocalStagingRegistry()
    private val resourceRepository = FakeResourceRepository()

    private lateinit var useCase: SaveDrawingUseCase

    private val bytes = byteArrayOf(1, 2, 3, 4)

    @Before
    fun setup() {
        useCase = SaveDrawingUseCase(fileOperationUseCase, stagingRegistry, resourceRepository, mockk(relaxed = true))
    }

    private fun seedLocalFile(name: String, content: ByteArray = byteArrayOf(9)): File =
        tempFolder.newFile(name).apply { writeBytes(content) }

    @Test
    fun `plain local drawing writes bytes and keeps name when unchanged`() = runTest {
        val current = seedLocalFile("drawing.jpg")

        val result = useCase(current, intendedName = "drawing.jpg", imageBytes = bytes, keepEditableCopy = false)

        val outcome = result.getOrThrow()
        assertEquals("drawing.jpg", outcome.finalName)
        assertFalse(outcome.renamedDueToConflict)
        assertTrue(outcome.isLocalSaveOnly)
        assertEquals(current.absolutePath, outcome.finalPath)
        assertTrue(bytes.contentEquals(File(outcome.finalPath).readBytes()))
    }

    @Test
    fun `plain local drawing renames to new free name and removes old file`() = runTest {
        val current = seedLocalFile("old.jpg")

        val result = useCase(current, intendedName = "renamed.jpg", imageBytes = bytes, keepEditableCopy = false)

        val outcome = result.getOrThrow()
        assertEquals("renamed.jpg", outcome.finalName)
        assertFalse(outcome.renamedDueToConflict)
        assertFalse(current.exists())
        assertTrue(File(current.parentFile, "renamed.jpg").exists())
    }

    @Test
    fun `plain local drawing applies seconds suffix on name conflict`() = runTest {
        val current = seedLocalFile("source.jpg")
        // Pre-existing collision target.
        File(current.parentFile, "target.jpg").writeBytes(byteArrayOf(0))

        val result = useCase(current, intendedName = "target.jpg", imageBytes = bytes, keepEditableCopy = false)

        val outcome = result.getOrThrow()
        assertTrue(outcome.renamedDueToConflict)
        assertTrue(outcome.finalName.matches(Regex("""target-\d{2}\.jpg""")))
    }

    @Test
    fun `deferred local drawing unregisters staging entry on save`() = runTest {
        val current = seedLocalFile("deferred.jpg")
        stagingRegistry.register(
            file = current,
            targetResourceId = 7L,
            targetParentPath = current.parent!!,
            intendedName = "deferred.jpg",
            kind = StagedKind.DRAWING,
            location = LocalStagingRegistry.Location.LOCAL_DEFERRED,
        )

        val result = useCase(current, intendedName = "deferred.jpg", imageBytes = bytes, keepEditableCopy = false)

        assertTrue(result.getOrThrow().isLocalSaveOnly)
        assertNull(stagingRegistry.lookup(current))
    }

    @Test
    fun `network staged drawing on copy success without editable copy reports remote save`() = runTest {
        val sessionDir = tempFolder.newFolder("drawing_session_1")
        val current = File(sessionDir, "net.jpg").apply { writeBytes(byteArrayOf(9)) }
        resourceRepository.setResources(listOf(createMediaResource(id = 3L, credentialsId = "cred-1")))
        stagingRegistry.register(
            file = current,
            targetResourceId = 3L,
            targetParentPath = "smb://host/share/dir",
            intendedName = "net.jpg",
            kind = StagedKind.DRAWING,
            location = LocalStagingRegistry.Location.NETWORK_STAGED,
        )
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val result = useCase(current, intendedName = "net.jpg", imageBytes = bytes, keepEditableCopy = false)

        val outcome = result.getOrThrow()
        assertFalse(outcome.isLocalSaveOnly)
        assertNull(outcome.remoteFailureMessage)
        assertNull(outcome.editableLocalPath)
        assertEquals("smb://host/share/dir/net.jpg", outcome.finalPath)
    }

    @Test
    fun `network staged drawing keeps editable copy re-registers staging entry`() = runTest {
        val sessionDir = tempFolder.newFolder("drawing_session_2")
        val current = File(sessionDir, "keep.jpg").apply { writeBytes(byteArrayOf(9)) }
        resourceRepository.setResources(listOf(createMediaResource(id = 4L)))
        stagingRegistry.register(
            file = current,
            targetResourceId = 4L,
            targetParentPath = "ftp://host/dir",
            intendedName = "keep.jpg",
            kind = StagedKind.DRAWING,
            location = LocalStagingRegistry.Location.NETWORK_STAGED,
        )
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val outcome = useCase(current, intendedName = "keep.jpg", imageBytes = bytes, keepEditableCopy = true).getOrThrow()

        assertFalse(outcome.isLocalSaveOnly)
        assertEquals(outcome.editableLocalPath, current.absolutePath)
        assertTrue(stagingRegistry.snapshot().any { it.localFile.absolutePath == current.absolutePath })
    }

    @Test
    fun `network staged drawing on copy failure falls back to local and keeps staging`() = runTest {
        val sessionDir = tempFolder.newFolder("drawing_session_3")
        val current = File(sessionDir, "fail.jpg").apply { writeBytes(byteArrayOf(9)) }
        resourceRepository.setResources(listOf(createMediaResource(id = 5L)))
        stagingRegistry.register(
            file = current,
            targetResourceId = 5L,
            targetParentPath = "smb://host/share",
            intendedName = "fail.jpg",
            kind = StagedKind.DRAWING,
            location = LocalStagingRegistry.Location.NETWORK_STAGED,
        )
        coEvery { fileOperationUseCase.execute(any()) } returns FileOperationResult.Failure("network down")

        val outcome = useCase(current, intendedName = "fail.jpg", imageBytes = bytes, keepEditableCopy = false).getOrThrow()

        assertTrue(outcome.isLocalSaveOnly)
        assertTrue(outcome.remoteFailureMessage!!.contains("network down"))
        assertTrue(stagingRegistry.snapshot().any { it.localFile.absolutePath == current.absolutePath })
    }

    @Test
    fun `blank name fails validation`() = runTest {
        val current = seedLocalFile("x.jpg")

        val result = useCase(current, intendedName = "   ", imageBytes = bytes, keepEditableCopy = false)

        assertTrue(result.isFailure)
    }

    @Test
    fun `forbidden characters in name fail validation`() = runTest {
        val current = seedLocalFile("x.jpg")

        val result = useCase(current, intendedName = "bad/name.jpg", imageBytes = bytes, keepEditableCopy = false)

        assertTrue(result.isFailure)
    }

    @Test
    fun `extensionless name inherits fallback extension from current file`() = runTest {
        val current = seedLocalFile("orig.png")

        val outcome = useCase(current, intendedName = "fresh", imageBytes = bytes, keepEditableCopy = false).getOrThrow()

        assertEquals("fresh.png", outcome.finalName)
    }
}
