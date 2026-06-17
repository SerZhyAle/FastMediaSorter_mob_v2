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

class SaveTextNoteUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fileOperationUseCase = mockk<FileOperationUseCase>()
    private val stagingRegistry = LocalStagingRegistry()
    private val resourceRepository = FakeResourceRepository()

    private lateinit var useCase: SaveTextNoteUseCase

    @Before
    fun setup() {
        useCase = SaveTextNoteUseCase(fileOperationUseCase, stagingRegistry, resourceRepository, mockk(relaxed = true))
    }

    @Test
    fun `pure-local save to free target name writes content without renaming`() = runTest {
        // currentLocalFile does not yet exist on disk (deferred-created note in a local dir).
        val dir = tempFolder.newFolder("local")
        val current = File(dir, "note.txt")

        val outcome = useCase(current, intendedName = "note.txt", content = "new").getOrThrow()

        assertTrue(outcome.isLocalSaveOnly)
        assertFalse(outcome.renamedDueToConflict)
        assertEquals("new", File(outcome.finalPath).readText())
    }

    @Test
    fun `pure-local save suffixes when the intended target already exists`() = runTest {
        // resolveLocal treats any existing target as a conflict and applies a seconds suffix.
        val current = tempFolder.newFile("note.txt").apply { writeText("old") }

        val outcome = useCase(current, intendedName = "note.txt", content = "new").getOrThrow()

        assertTrue(outcome.renamedDueToConflict)
        assertTrue(outcome.finalName.matches(Regex("""note-\d{2}\.txt""")))
    }

    @Test
    fun `pure-local save with new free name deletes old file`() = runTest {
        val current = tempFolder.newFile("old.txt").apply { writeText("x") }

        val outcome = useCase(current, intendedName = "new.txt", content = "body").getOrThrow()

        assertEquals("new.txt", outcome.finalName)
        assertFalse(current.exists())
        assertEquals("body", File(current.parentFile, "new.txt").readText())
    }

    @Test
    fun `pure-local save applies suffix on conflict`() = runTest {
        val current = tempFolder.newFile("src.txt").apply { writeText("x") }
        File(current.parentFile, "dst.txt").writeText("existing")

        val outcome = useCase(current, intendedName = "dst.txt", content = "body").getOrThrow()

        assertTrue(outcome.renamedDueToConflict)
        assertTrue(outcome.finalName.matches(Regex("""dst-\d{2}\.txt""")))
    }

    @Test
    fun `local deferred note writes to target parent and unregisters`() = runTest {
        val targetDir = tempFolder.newFolder("local_target")
        val current = tempFolder.newFile("deferred.txt")
        stagingRegistry.register(
            file = current,
            targetResourceId = 1L,
            targetParentPath = targetDir.absolutePath,
            intendedName = "deferred.txt",
            kind = StagedKind.TEXT_NOTE,
            location = LocalStagingRegistry.Location.LOCAL_DEFERRED,
        )

        val outcome = useCase(current, intendedName = "deferred.txt", content = "hi").getOrThrow()

        assertTrue(outcome.isLocalSaveOnly)
        assertEquals("hi", File(targetDir, "deferred.txt").readText())
        assertNull(stagingRegistry.lookup(current))
    }

    @Test
    fun `network staged note on copy success uploads and removes staging`() = runTest {
        val stagingDir = tempFolder.newFolder("staging")
        val current = File(stagingDir, "remote.txt").apply { writeText("old") }
        resourceRepository.setResources(listOf(createMediaResource(id = 2L, credentialsId = "c")))
        stagingRegistry.register(
            file = current,
            targetResourceId = 2L,
            targetParentPath = "smb://h/share",
            intendedName = "remote.txt",
            kind = StagedKind.TEXT_NOTE,
            location = LocalStagingRegistry.Location.NETWORK_STAGED,
        )
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val outcome = useCase(current, intendedName = "remote.txt", content = "new").getOrThrow()

        assertFalse(outcome.isLocalSaveOnly)
        assertNull(outcome.remoteFailureMessage)
        assertEquals("smb://h/share/remote.txt", outcome.finalPath)
        assertFalse(current.exists())
        assertNull(stagingRegistry.lookup(current))
    }

    @Test
    fun `network staged note on copy failure keeps local staging file`() = runTest {
        val stagingDir = tempFolder.newFolder("staging2")
        val current = File(stagingDir, "fail.txt").apply { writeText("old") }
        resourceRepository.setResources(listOf(createMediaResource(id = 3L)))
        stagingRegistry.register(
            file = current,
            targetResourceId = 3L,
            targetParentPath = "ftp://h/dir",
            intendedName = "fail.txt",
            kind = StagedKind.TEXT_NOTE,
            location = LocalStagingRegistry.Location.NETWORK_STAGED,
        )
        coEvery { fileOperationUseCase.execute(any()) } returns FileOperationResult.Failure("offline")

        val outcome = useCase(current, intendedName = "fail.txt", content = "new").getOrThrow()

        assertTrue(outcome.isLocalSaveOnly)
        assertTrue(outcome.remoteFailureMessage!!.contains("offline"))
        assertEquals("new", current.readText())
    }
}
