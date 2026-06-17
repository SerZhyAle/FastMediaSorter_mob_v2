package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

/**
 * JVM coverage for [ArchiveFilesUseCase]. Exercises local-file and error branches with a real ZIP
 * built under [TemporaryFolder]; content:// sources use Uri.parse and are not covered here.
 */
class ArchiveFilesUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private lateinit var useCase: ArchiveFilesUseCase

    @Before
    fun setup() {
        every { context.getString(any()) } returns "error"
        every { context.getString(any(), *anyVararg()) } returns "error"
        useCase = ArchiveFilesUseCase(context, mockk(relaxed = true))
    }

    @Test
    fun `empty file list emits error`() = runTest {
        val dest = tempFolder.newFolder("out")
        val events = useCase(emptyList(), "a.zip", dest.absolutePath).toList()

        assertTrue(events.single() is ArchiveProgress.Error)
    }

    @Test
    fun `missing destination directory emits error`() = runTest {
        val src = tempFolder.newFile("f.txt").apply { writeText("x") }
        val events = useCase(listOf(src.absolutePath), "a.zip", "/no/such/dir").toList()

        assertTrue(events.single() is ArchiveProgress.Error)
    }

    @Test
    fun `archives local files and produces a readable zip`() = runTest {
        val dest = tempFolder.newFolder("out")
        val f1 = tempFolder.newFile("one.txt").apply { writeText("hello") }
        val f2 = tempFolder.newFile("two.txt").apply { writeText("world") }

        val events = useCase(listOf(f1.absolutePath, f2.absolutePath), "bundle", dest.absolutePath).toList()

        assertTrue(events.first() is ArchiveProgress.Started)
        assertEquals(2, (events.first() as ArchiveProgress.Started).totalFiles)
        val success = events.filterIsInstance<ArchiveProgress.Success>().single()
        assertEquals(2, success.archivedCount)
        // ".zip" extension appended when missing.
        assertTrue(success.archivePath.endsWith("bundle.zip"))

        ZipFile(File(success.archivePath)).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertEquals(setOf("one.txt", "two.txt"), names)
            assertEquals("hello", zip.getInputStream(zip.getEntry("one.txt")).readBytes().toString(Charsets.UTF_8))
        }
    }

    @Test
    fun `remote path is skipped with a file warning`() = runTest {
        val dest = tempFolder.newFolder("out")
        val local = tempFolder.newFile("ok.txt").apply { writeText("x") }

        val events = useCase(
            listOf("smb://host/share/remote.txt", local.absolutePath),
            "mix.zip",
            dest.absolutePath,
        ).toList()

        assertTrue(events.any { it is ArchiveProgress.FileWarning })
        assertEquals(1, events.filterIsInstance<ArchiveProgress.Success>().single().archivedCount)
    }

    @Test
    fun `nonexistent file is skipped via warning`() = runTest {
        val dest = tempFolder.newFolder("out")

        val events = useCase(
            listOf(File(tempFolder.root, "ghost.txt").absolutePath),
            "x.zip",
            dest.absolutePath,
        ).toList()

        assertTrue(events.any { it is ArchiveProgress.FileWarning })
        assertEquals(0, events.filterIsInstance<ArchiveProgress.Success>().single().archivedCount)
    }

    @Test
    fun `generateUniqueFile appends numeric suffix on collision`() {
        val dir = tempFolder.newFolder("u")
        File(dir, "a.zip").apply { writeText("x") }

        val candidate = useCase.generateUniqueFile(dir, "a.zip")

        assertEquals("a_1.zip", candidate.name)
    }

    @Test
    fun `generateUniqueFile returns base name when free`() {
        val dir = tempFolder.newFolder("u")
        val candidate = useCase.generateUniqueFile(dir, "fresh.zip")
        assertNotNull(candidate)
        assertEquals("fresh.zip", candidate.name)
    }
}
